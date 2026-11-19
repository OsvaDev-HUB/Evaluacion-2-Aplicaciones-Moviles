// Pruebas de firestore.rules con el emulador de Firestore.
// Revisan que cada usuario solo pueda ver y cambiar sus propios datos, y que los datos tengan el formato correcto.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { addDoc, collection, deleteDoc, doc, getDoc, getDocs, setDoc, setLogLevel, updateDoc } from 'firebase/firestore';

// Los rechazos son lo esperado en varias pruebas, así que no se muestran como avisos.
setLogLevel('silent');

let entorno;

before(async () => {
  entorno = await initializeTestEnvironment({
    projectId: 'demo-climapulse',
    firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await entorno.cleanup();
});

beforeEach(async () => {
  await entorno.clearFirestore();
});

const ana = () => entorno.authenticatedContext('ana').firestore();
const beto = () => entorno.authenticatedContext('beto').firestore();
const anonimo = () => entorno.unauthenticatedContext().firestore();

const perfil = { nombre: 'Ana', telefono: '+56 9 1234 5678' };
const lectura = () => ({ temperatura: 22.5, humedad: 48, luz: 420, fecha: Date.now(), origen: 'Nodo sensor' });

// Crea datos saltándose las reglas, como si ya existieran.
async function sembrar(ruta, datos) {
  await entorno.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), ruta), datos));
}

describe('perfil users/{uid}', () => {
  it('el dueño puede crear, leer y editar su perfil', async () => {
    await assertSucceeds(setDoc(doc(ana(), 'users/ana'), perfil));
    await assertSucceeds(getDoc(doc(ana(), 'users/ana')));
    await assertSucceeds(updateDoc(doc(ana(), 'users/ana'), { telefono: '' }));
  });

  it('otro usuario no puede leer ni cambiar el perfil', async () => {
    await sembrar('users/ana', perfil);
    await assertFails(getDoc(doc(beto(), 'users/ana')));
    await assertFails(setDoc(doc(beto(), 'users/ana'), perfil));
    await assertFails(deleteDoc(doc(beto(), 'users/ana')));
  });

  it('sin sesión no se puede leer nada', async () => {
    await sembrar('users/ana', perfil);
    await assertFails(getDoc(doc(anonimo(), 'users/ana')));
  });

  it('rechaza campos extra o con formato incorrecto', async () => {
    await assertFails(setDoc(doc(ana(), 'users/ana'), { ...perfil, admin: true }));
    await assertFails(setDoc(doc(ana(), 'users/ana'), { nombre: '', telefono: '' }));
    await assertFails(setDoc(doc(ana(), 'users/ana'), { nombre: 'x'.repeat(61), telefono: '' }));
    await assertFails(setDoc(doc(ana(), 'users/ana'), { nombre: 'Ana', telefono: 123 }));
  });

  it('el dueño puede borrar su perfil', async () => {
    await sembrar('users/ana', perfil);
    await assertSucceeds(deleteDoc(doc(ana(), 'users/ana')));
  });
});

describe('lecturas users/{uid}/lecturas', () => {
  it('el dueño puede guardar, leer y borrar sus lecturas', async () => {
    const ref = await assertSucceeds(addDoc(collection(ana(), 'users/ana/lecturas'), lectura()));
    await assertSucceeds(getDocs(collection(ana(), 'users/ana/lecturas')));
    await assertSucceeds(deleteDoc(doc(ana(), ref.path)));
  });

  it('otro usuario no puede ver, agregar ni borrar lecturas ajenas', async () => {
    await sembrar('users/ana/lecturas/l1', lectura());
    await assertFails(getDocs(collection(beto(), 'users/ana/lecturas')));
    await assertFails(addDoc(collection(beto(), 'users/ana/lecturas'), lectura()));
    await assertFails(deleteDoc(doc(beto(), 'users/ana/lecturas/l1')));
  });

  it('una lectura guardada no se puede editar', async () => {
    await sembrar('users/ana/lecturas/l1', lectura());
    await assertFails(updateDoc(doc(ana(), 'users/ana/lecturas/l1'), { temperatura: 99 }));
  });

  it('rechaza valores fuera de rango', async () => {
    const col = collection(ana(), 'users/ana/lecturas');
    await assertFails(addDoc(col, { ...lectura(), temperatura: 500 }));
    await assertFails(addDoc(col, { ...lectura(), humedad: 101 }));
    await assertFails(addDoc(col, { ...lectura(), luz: -1 }));
  });

  it('rechaza fechas del futuro y tipos incorrectos', async () => {
    const col = collection(ana(), 'users/ana/lecturas');
    await assertFails(addDoc(col, { ...lectura(), fecha: Date.now() + 60 * 60 * 1000 }));
    await assertFails(addDoc(col, { ...lectura(), fecha: 'hoy' }));
    await assertFails(addDoc(col, { ...lectura(), temperatura: '22' }));
  });

  it('rechaza campos faltantes o de más', async () => {
    const col = collection(ana(), 'users/ana/lecturas');
    const { origen, ...sinOrigen } = lectura();
    await assertFails(addDoc(col, sinOrigen));
    await assertFails(addDoc(col, { ...lectura(), extra: true }));
    await assertFails(addDoc(col, { ...lectura(), origen: 'x'.repeat(41) }));
  });
});

describe('resto de la base', () => {
  it('cualquier otra colección está cerrada', async () => {
    await assertFails(setDoc(doc(ana(), 'otra/cosa'), { a: 1 }));
    await assertFails(getDoc(doc(ana(), 'otra/cosa')));
  });
});
