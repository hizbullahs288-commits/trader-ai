const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("positions").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("journal").doc("j1").set({
      userId: ALICE_UID,
      symbol: "BTC-USD",
      assetClass: "CRYPTO",
      side: "LONG",
      entryPrice: 90000,
      exitPrice: 92000,
      quantity: 0.1,
      pnlAmount: 200,
      pnlPercent: 2.22,
      strategyTag: "EMA Pullback",
      emotionTag: "Disciplined",
      notes: "Clean setup",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("UserAccount: owner can create, read, and update valid balances; cross-user and tampered writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      virtualBalance: 100000.0,
      initialCapital: 100000.0,
      realizedPnl: 0.0,
      totalTrades: 0,
      winningTrades: 0,
      defaultRiskPercent: 1.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid risk update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      defaultRiskPercent: 2.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject winningTrades > totalTrades
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      winningTrades: 5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject cross-user read or write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      virtualBalance: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      defaultRiskPercent: 1.5,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("PaperPosition: create OPEN, update SL/TP, close position, and enforce terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create OPEN position (no @DocumentId id field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_1").set({
      userId: ALICE_UID,
      symbol: "BTC-USD",
      displayName: "Bitcoin / USD",
      assetClass: "CRYPTO",
      side: "LONG",
      status: "OPEN",
      quantity: 0.25,
      entryPrice: 91000.0,
      stopLoss: 89000.0,
      takeProfit: 95000.0,
      notes: "Breakout above EMA20",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject extra id field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_bad").set({
      id: "pos_bad",
      userId: ALICE_UID,
      symbol: "BTC-USD",
      displayName: "Bitcoin / USD",
      assetClass: "CRYPTO",
      side: "LONG",
      status: "OPEN",
      quantity: 0.25,
      entryPrice: 91000.0,
      notes: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Update SL/TP while OPEN
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_1").update({
      stopLoss: 90000.0,
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Close position
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_1").update({
      status: "CLOSED",
      exitPrice: 94000.0,
      realizedPnl: 750.0,
      closedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: cannot reopen or edit once CLOSED
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_1").update({
      status: "OPEN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List query matching Kotlin repository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("positions").get()
  );

  // Cross-user list/get denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("positions").get()
  );
});

test("JournalEntry: create, list, update notes/tags, delete, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 8000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal").doc("j_1").set({
      userId: ALICE_UID,
      symbol: "EURUSD=X",
      assetClass: "FOREX",
      side: "SHORT",
      entryPrice: 1.085,
      exitPrice: 1.079,
      quantity: 10000,
      pnlAmount: 60.0,
      pnlPercent: 0.55,
      strategyTag: "RSI Overbought",
      emotionTag: "Patient",
      notes: "Followed plan strictly.",
      linkedPositionId: "pos_1",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal").doc("j_1").update({
      notes: "Followed plan strictly. Scaled out near support.",
      strategyTag: "AI Signal",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / immutable field change
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal").doc("j_1").update({
      pnlAmount: 99999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal").get()
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("journal").doc("j_1").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal").doc("j_1").delete()
  );
});
