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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

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

test("Unauthenticated user cannot read or write any financial collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("workShifts").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("expenses").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("debts").doc("d1").set({
      userId: ALICE_UID,
      direction: "I_OWE",
      personName: "Bank",
      totalAmount: 1000,
      paidAmount: 0,
      currency: "TRY",
      dateIso: "2026-11-18",
      status: "UNPAID",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("User profile create, partial update, and shadow field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      primaryCurrency: "TRY",
      monthlyIncomeGoal: 45000,
      monthlyBudgetLimit: 25000,
      rateTryToUsd: 34.5,
      rateTmtToUsd: 3.5,
      rateEurToUsd: 0.92,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      primaryCurrency: "USD",
      monthlyIncomeGoal: 5000,
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      monthlyIncomeGoal: 6000,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("WorkShifts create, update, list, and @DocumentId id rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);
  const shiftsCol = aliceDb.collection("users").doc(ALICE_UID).collection("workShifts");

  await assertSucceeds(
    shiftsCol.doc("shift_1").set({
      userId: ALICE_UID,
      dateIso: "2026-11-18",
      companyName: "Main Job",
      startTime: "08:00",
      endTime: "17:00",
      overnight: false,
      durationMinutes: 540,
      payType: "FIXED",
      rateOrFixedAmount: 1500,
      totalEarnings: 1500,
      currency: "TRY",
      notes: "Day shift",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(shiftsCol.get());

  await assertSucceeds(
    shiftsCol.doc("shift_1").update({
      totalEarnings: 1800,
      updatedAt: pastDate(1000),
    })
  );

  // Reject extra @DocumentId 'id' field in payload
  await assertFails(
    shiftsCol.doc("shift_2").set({
      id: "shift_2",
      userId: ALICE_UID,
      dateIso: "2026-11-18",
      companyName: "Evening Job",
      startTime: "19:00",
      endTime: "02:00",
      overnight: true,
      durationMinutes: 420,
      payType: "HOURLY",
      rateOrFixedAmount: 200,
      totalEarnings: 1400,
      currency: "TRY",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Expenses, Categories, Budgets, Savings, Debts, and Recurring Expenses parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);
  const created = pastDate(10000);

  await assertSucceeds(
    userRef.collection("categories").doc("cat_food").set({
      userId: ALICE_UID,
      name: "Food",
      iconKey: "restaurant",
      colorHex: 0xFFFF9500,
      defaultKey: "food",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    userRef.collection("expenses").doc("exp_1").set({
      userId: ALICE_UID,
      dateIso: "2026-11-18",
      amount: 320.5,
      currency: "TRY",
      categoryId: "cat_food",
      categoryName: "Food",
      description: "Lunch",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    userRef.collection("categoryBudgets").doc("b_1").set({
      userId: ALICE_UID,
      categoryId: "cat_food",
      categoryName: "Food",
      limitAmount: 5000,
      currency: "TRY",
      monthKey: "2026-11",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    userRef.collection("savingsGoals").doc("g_1").set({
      userId: ALICE_UID,
      title: "Emergency Fund",
      targetAmount: 100000,
      currentAmount: 25000,
      currency: "TRY",
      targetDateIso: "2027-06-01",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    userRef.collection("savingsTransfers").doc("t_1").set({
      userId: ALICE_UID,
      goalId: "g_1",
      goalTitle: "Emergency Fund",
      amount: 5000,
      currency: "TRY",
      dateIso: "2026-11-18",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    userRef.collection("debts").doc("d_1").set({
      userId: ALICE_UID,
      direction: "OWED_TO_ME",
      personName: "Murat",
      totalAmount: 2000,
      paidAmount: 500,
      currency: "TRY",
      dateIso: "2026-11-01",
      dueDateIso: "2026-12-01",
      status: "PARTIAL",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject paidAmount > totalAmount
  await assertFails(
    userRef.collection("debts").doc("d_1").update({
      paidAmount: 2500,
      status: "PAID",
      updatedAt: pastDate(1000),
    })
  );

  await assertSucceeds(
    userRef.collection("recurringExpenses").doc("r_1").set({
      userId: ALICE_UID,
      title: "Home Internet",
      amount: 450,
      currency: "TRY",
      categoryId: "cat_internet",
      categoryName: "Phone & Internet",
      frequency: "MONTHLY",
      nextDueDateIso: "2026-12-01",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation prevents Bob from reading or modifying Alice records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("alice_exp")
      .set({
        userId: ALICE_UID,
        dateIso: "2026-11-18",
        amount: 500,
        currency: "TRY",
        categoryId: "cat_food",
        categoryName: "Food",
        createdAt: pastDate(10000),
        updatedAt: pastDate(10000),
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("expenses").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("alice_exp")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("alice_exp")
      .delete()
  );
});
