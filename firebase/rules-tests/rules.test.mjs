// Pruebas de las reglas de Firestore. Se ejecutan con:
//   cd firebase && firebase emulators:exec --only firestore "npm --prefix rules-tests test"
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, getDocs, collection } from 'firebase/firestore';

const aqui = path.dirname(fileURLToPath(import.meta.url));
let entorno;

const valido = (extra = {}) => ({
  titulo: 'Estudiar Firestore',
  descripcion: 'Reglas por usuario',
  categoria: 'ESTUDIO',
  prioridad: 'ALTA',
  estado: 'PENDIENTE',
  fecha: 1000,
  actualizadoEn: 1000,
  ...extra,
});

before(async () => {
  const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080').split(':');
  entorno = await initializeTestEnvironment({
    projectId: 'demo-recordatorios',
    firestore: {
      rules: readFileSync(path.join(aqui, '..', 'firestore.rules'), 'utf8'),
      host,
      port: Number(port),
    },
  });
});

after(async () => entorno.cleanup());

beforeEach(async () => {
  await entorno.clearFirestore();
  // Dato inicial de la cuenta A, cargado sin aplicar reglas.
  await entorno.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/ana/reminders/r1'), valido());
  });
});

const comoAna = () => entorno.authenticatedContext('ana').firestore();
const comoBeto = () => entorno.authenticatedContext('beto').firestore();
const anonimo = () => entorno.unauthenticatedContext().firestore();

test('el duenio puede leer su recordatorio', async () => {
  await assertSucceeds(getDoc(doc(comoAna(), 'users/ana/reminders/r1')));
});

test('el duenio puede crear, actualizar y eliminar', async () => {
  const db = comoAna();
  await assertSucceeds(setDoc(doc(db, 'users/ana/reminders/r2'), valido()));
  await assertSucceeds(setDoc(doc(db, 'users/ana/reminders/r2'), valido({ titulo: 'Titulo editado' })));
  await assertSucceeds(deleteDoc(doc(db, 'users/ana/reminders/r2')));
});

test('otra cuenta NO puede leer los recordatorios ajenos', async () => {
  await assertFails(getDoc(doc(comoBeto(), 'users/ana/reminders/r1')));
});

test('otra cuenta NO puede listar la coleccion ajena', async () => {
  await assertFails(getDocs(collection(comoBeto(), 'users/ana/reminders')));
});

test('otra cuenta NO puede crear, modificar ni eliminar en una cuenta ajena', async () => {
  const db = comoBeto();
  await assertFails(setDoc(doc(db, 'users/ana/reminders/r3'), valido()));
  await assertFails(setDoc(doc(db, 'users/ana/reminders/r1'), valido({ titulo: 'Pisado por Beto' })));
  await assertFails(deleteDoc(doc(db, 'users/ana/reminders/r1')));
});

test('sin autenticacion no se puede leer ni escribir', async () => {
  await assertFails(getDoc(doc(anonimo(), 'users/ana/reminders/r1')));
  await assertFails(setDoc(doc(anonimo(), 'users/ana/reminders/r4'), valido()));
});

test('el servidor rechaza un titulo de menos de 4 caracteres', async () => {
  await assertFails(setDoc(doc(comoAna(), 'users/ana/reminders/r5'), valido({ titulo: 'ab' })));
});

test('el servidor rechaza valores fuera de las opciones definidas', async () => {
  const db = comoAna();
  await assertFails(setDoc(doc(db, 'users/ana/reminders/r6'), valido({ categoria: 'OTRA' })));
  await assertFails(setDoc(doc(db, 'users/ana/reminders/r7'), valido({ prioridad: 'URGENTE' })));
  await assertFails(setDoc(doc(db, 'users/ana/reminders/r8'), valido({ estado: 'BORRADO' })));
});

test('el servidor rechaza campos no previstos y descripciones demasiado largas', async () => {
  const db = comoAna();
  await assertFails(setDoc(doc(db, 'users/ana/reminders/r9'), valido({ admin: true })));
  await assertFails(setDoc(doc(db, 'users/ana/reminders/r10'), valido({ descripcion: 'x'.repeat(201) })));
});

test('acepta una descripcion nula', async () => {
  await assertSucceeds(setDoc(doc(comoAna(), 'users/ana/reminders/r11'), valido({ descripcion: null })));
});

test('las demas rutas estan cerradas', async () => {
  await assertFails(setDoc(doc(comoAna(), 'otra/coleccion'), { x: 1 }));
  await assertFails(getDoc(doc(comoAna(), 'users/ana')));
});
