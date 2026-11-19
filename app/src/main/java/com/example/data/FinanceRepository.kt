package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.CategoryBudget
import com.example.domain.DateUtils
import com.example.domain.DebtDirection
import com.example.domain.DebtRecord
import com.example.domain.DebtStatus
import com.example.domain.ExpenseCategory
import com.example.domain.ExpenseRecord
import com.example.domain.RecurringExpense
import com.example.domain.RecurringFrequency
import com.example.domain.SavingsGoal
import com.example.domain.SavingsTransfer
import com.example.domain.UserProfile
import com.example.domain.WorkPayType
import com.example.domain.WorkShift
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> observeSubcollection(
        userId: String,
        collectionName: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/$collectionName"
        emitAll(
            userDoc(uid).collection(collectionName)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid"
        emitAll(
            userDoc(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeWorkShifts(userId: String): Flow<List<WorkShift>> =
        observeSubcollection(userId, "workShifts", WorkShift::class.java)

    fun observeExpenses(userId: String): Flow<List<ExpenseRecord>> =
        observeSubcollection(userId, "expenses", ExpenseRecord::class.java)

    fun observeCategories(userId: String): Flow<List<ExpenseCategory>> =
        observeSubcollection(userId, "categories", ExpenseCategory::class.java)

    fun observeSavingsGoals(userId: String): Flow<List<SavingsGoal>> =
        observeSubcollection(userId, "savingsGoals", SavingsGoal::class.java)

    fun observeSavingsTransfers(userId: String): Flow<List<SavingsTransfer>> =
        observeSubcollection(userId, "savingsTransfers", SavingsTransfer::class.java)

    fun observeDebts(userId: String): Flow<List<DebtRecord>> =
        observeSubcollection(userId, "debts", DebtRecord::class.java)

    fun observeRecurringExpenses(userId: String): Flow<List<RecurringExpense>> =
        observeSubcollection(userId, "recurringExpenses", RecurringExpense::class.java)

    fun observeCategoryBudgets(userId: String): Flow<List<CategoryBudget>> =
        observeSubcollection(userId, "categoryBudgets", CategoryBudget::class.java)

    suspend fun ensureUserInitialized(
        userId: String,
        displayName: String,
        email: String?
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName.trim().ifBlank { "Finance User" }.take(100)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email?.take(150),
                    "primaryCurrency" to "TRY",
                    "monthlyIncomeGoal" to 45000.0,
                    "monthlyBudgetLimit" to 25000.0,
                    "rateTryToUsd" to 34.5,
                    "rateTmtToUsd" to 3.5,
                    "rateEurToUsd" to 0.92,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            val catsSnap = docRef.collection("categories").limit(1).get().await()
            if (catsSnap.isEmpty) {
                seedDefaultCategories(uid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedDefaultCategories(uid: String) {
        val defaults = listOf(
            Triple("cat_transport", "Transportation / Travel", Pair("directions_car", 0xFF0A84FF)),
            Triple("cat_food", "Food", Pair("restaurant", 0xFFFF9500)),
            Triple("cat_housing", "Housing / Rent", Pair("home", 0xFF5E5CE6)),
            Triple("cat_shopping", "Shopping", Pair("shopping_bag", 0xFFEC4899)),
            Triple("cat_phone", "Phone & Internet", Pair("wifi", 0xFF06B6D4)),
            Triple("cat_debt", "Debt Payments", Pair("credit_card", 0xFFEF4444)),
            Triple("cat_entertainment", "Entertainment", Pair("movie", 0xFF8B5CF6)),
            Triple("cat_health", "Health", Pair("favorite", 0xFF10B981)),
            Triple("cat_other", "Other Expenses", Pair("category", 0xFF64748B))
        )
        val batch = db.batch()
        val col = userDoc(uid).collection("categories")
        defaults.forEach { (id, name, style) ->
            val defaultKey = id.removePrefix("cat_")
            val data = mapOf(
                "userId" to uid,
                "name" to name,
                "iconKey" to style.first,
                "colorHex" to style.second,
                "defaultKey" to defaultKey,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(col.document(id), data)
        }
        batch.commit().await()
    }

    suspend fun updateProfilePreferences(
        primaryCurrency: String,
        monthlyIncomeGoal: Double,
        monthlyBudgetLimit: Double,
        rateTryToUsd: Double,
        rateTmtToUsd: Double,
        rateEurToUsd: Double
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            require(monthlyIncomeGoal >= 0.0)
            require(monthlyBudgetLimit >= 0.0)
            require(rateTryToUsd > 0.0 && rateTmtToUsd > 0.0 && rateEurToUsd > 0.0)
            userDoc(uid).update(
                mapOf(
                    "primaryCurrency" to primaryCurrency,
                    "monthlyIncomeGoal" to monthlyIncomeGoal,
                    "monthlyBudgetLimit" to monthlyBudgetLimit,
                    "rateTryToUsd" to rateTryToUsd,
                    "rateTmtToUsd" to rateTmtToUsd,
                    "rateEurToUsd" to rateEurToUsd,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(displayName: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val safeName = displayName.trim().ifBlank { "Finance User" }.take(100)
            userDoc(uid).update(
                mapOf(
                    "displayName" to safeName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveWorkShift(shift: WorkShift): Result<Unit> {
        var path = "workShifts"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("workShifts")
            val isNew = shift.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(shift.id)
            path = docRef.path
            val company = shift.companyName.trim().take(100)
            require(company.isNotEmpty())
            require(shift.durationMinutes in 1L..1440L)
            require(shift.rateOrFixedAmount >= 0.0 && shift.totalEarnings >= 0.0)
            val notes = shift.notes?.trim()?.take(500)?.ifEmpty { null }

            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "dateIso" to shift.dateIso,
                    "companyName" to company,
                    "startTime" to shift.startTime,
                    "endTime" to shift.endTime,
                    "overnight" to shift.overnight,
                    "durationMinutes" to shift.durationMinutes,
                    "payType" to shift.payType,
                    "rateOrFixedAmount" to shift.rateOrFixedAmount,
                    "totalEarnings" to shift.totalEarnings,
                    "currency" to shift.currency,
                    "notes" to notes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "dateIso" to shift.dateIso,
                    "companyName" to company,
                    "startTime" to shift.startTime,
                    "endTime" to shift.endTime,
                    "overnight" to shift.overnight,
                    "durationMinutes" to shift.durationMinutes,
                    "payType" to shift.payType,
                    "rateOrFixedAmount" to shift.rateOrFixedAmount,
                    "totalEarnings" to shift.totalEarnings,
                    "currency" to shift.currency,
                    "notes" to (notes ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(collectionName: String, docId: String): Result<Unit> {
        var path = collectionName
        return try {
            val uid = requireUserId()
            require(docId.isNotBlank())
            val docRef = userDoc(uid).collection(collectionName).document(docId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveExpense(expense: ExpenseRecord): Result<Unit> {
        var path = "expenses"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("expenses")
            val isNew = expense.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(expense.id)
            path = docRef.path
            require(expense.amount > 0.0)
            val catName = expense.categoryName.trim().take(60).ifEmpty { "Other" }
            val desc = expense.description?.trim()?.take(500)?.ifEmpty { null }
            val recId = expense.recurringId?.trim()?.take(64)?.ifEmpty { null }

            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "dateIso" to expense.dateIso,
                    "amount" to expense.amount,
                    "currency" to expense.currency,
                    "categoryId" to expense.categoryId.take(64),
                    "categoryName" to catName,
                    "description" to desc,
                    "recurringId" to recId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "dateIso" to expense.dateIso,
                    "amount" to expense.amount,
                    "currency" to expense.currency,
                    "categoryId" to expense.categoryId.take(64),
                    "categoryName" to catName,
                    "description" to (desc ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (recId != null) updates["recurringId"] = recId
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveCategory(category: ExpenseCategory): Result<Unit> {
        var path = "categories"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("categories")
            val isNew = category.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(category.id)
            path = docRef.path
            val name = category.name.trim().take(60)
            require(name.isNotEmpty())

            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to name,
                    "iconKey" to category.iconKey.take(40),
                    "colorHex" to category.colorHex,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to name,
                        "iconKey" to category.iconKey.take(40),
                        "colorHex" to category.colorHex,
                        "defaultKey" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveSavingsGoal(goal: SavingsGoal): Result<Unit> {
        var path = "savingsGoals"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("savingsGoals")
            val isNew = goal.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(goal.id)
            path = docRef.path
            val title = goal.title.trim().take(80)
            require(title.isNotEmpty() && goal.targetAmount > 0.0 && goal.currentAmount >= 0.0)
            val targetDate = goal.targetDateIso?.trim()?.take(10)?.ifEmpty { null }

            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to title,
                    "targetAmount" to goal.targetAmount,
                    "currentAmount" to goal.currentAmount,
                    "currency" to goal.currency,
                    "targetDateIso" to targetDate,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to title,
                        "targetAmount" to goal.targetAmount,
                        "currentAmount" to goal.currentAmount,
                        "currency" to goal.currency,
                        "targetDateIso" to (targetDate ?: FieldValue.delete()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun transferToSavingsGoal(
        goal: SavingsGoal,
        amount: Double,
        dateIso: String,
        note: String?
    ): Result<Unit> {
        var path = "savingsTransfers"
        return try {
            val uid = requireUserId()
            require(goal.id.isNotBlank() && amount > 0.0)
            val goalRef = userDoc(uid).collection("savingsGoals").document(goal.id)
            val transferRef = userDoc(uid).collection("savingsTransfers").document()
            path = transferRef.path

            val batch = db.batch()
            batch.update(
                goalRef,
                mapOf(
                    "currentAmount" to (goal.currentAmount + amount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val cleanNote = note?.trim()?.take(300)?.ifEmpty { null }
            val transferPayload = mapOf(
                "userId" to uid,
                "goalId" to goal.id,
                "goalTitle" to goal.title.take(80),
                "amount" to amount,
                "currency" to goal.currency,
                "dateIso" to dateIso,
                "note" to cleanNote,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            batch.set(transferRef, transferPayload)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveDebt(debt: DebtRecord): Result<Unit> {
        var path = "debts"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("debts")
            val isNew = debt.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(debt.id)
            path = docRef.path
            val person = debt.personName.trim().take(100)
            require(person.isNotEmpty() && debt.totalAmount > 0.0)
            val safePaid = debt.paidAmount.coerceIn(0.0, debt.totalAmount)
            val computedStatus = when {
                safePaid >= debt.totalAmount - 0.005 -> DebtStatus.PAID.name
                safePaid > 0.005 -> DebtStatus.PARTIAL.name
                else -> DebtStatus.UNPAID.name
            }
            val due = debt.dueDateIso?.trim()?.take(10)?.ifEmpty { null }
            val note = debt.note?.trim()?.take(500)?.ifEmpty { null }

            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "direction" to debt.direction,
                    "personName" to person,
                    "totalAmount" to debt.totalAmount,
                    "paidAmount" to safePaid,
                    "currency" to debt.currency,
                    "dateIso" to debt.dateIso,
                    "dueDateIso" to due,
                    "status" to computedStatus,
                    "note" to note,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "direction" to debt.direction,
                        "personName" to person,
                        "totalAmount" to debt.totalAmount,
                        "paidAmount" to safePaid,
                        "currency" to debt.currency,
                        "dateIso" to debt.dateIso,
                        "dueDateIso" to (due ?: FieldValue.delete()),
                        "status" to computedStatus,
                        "note" to (note ?: FieldValue.delete()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveRecurringExpense(item: RecurringExpense): Result<Unit> {
        var path = "recurringExpenses"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("recurringExpenses")
            val isNew = item.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(item.id)
            path = docRef.path
            val title = item.title.trim().take(100)
            require(title.isNotEmpty() && item.amount > 0.0)
            val note = item.note?.trim()?.take(300)?.ifEmpty { null }

            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to title,
                    "amount" to item.amount,
                    "currency" to item.currency,
                    "categoryId" to item.categoryId.take(64),
                    "categoryName" to item.categoryName.take(60),
                    "frequency" to item.frequency,
                    "nextDueDateIso" to item.nextDueDateIso,
                    "active" to item.active,
                    "note" to note,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to title,
                        "amount" to item.amount,
                        "currency" to item.currency,
                        "categoryId" to item.categoryId.take(64),
                        "categoryName" to item.categoryName.take(60),
                        "frequency" to item.frequency,
                        "nextDueDateIso" to item.nextDueDateIso,
                        "active" to item.active,
                        "note" to (note ?: FieldValue.delete()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun logRecurringExpenseNow(item: RecurringExpense, dateIso: String): Result<Unit> {
        var path = "recurringExpenses"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank())
            val recRef = userDoc(uid).collection("recurringExpenses").document(item.id)
            val expRef = userDoc(uid).collection("expenses").document()
            path = recRef.path
            val nextDate = DateUtils.nextRecurringDate(item.nextDueDateIso, item.frequencyEnum())

            val batch = db.batch()
            val expPayload = mapOf(
                "userId" to uid,
                "dateIso" to dateIso,
                "amount" to item.amount,
                "currency" to item.currency,
                "categoryId" to item.categoryId.take(64),
                "categoryName" to item.categoryName.take(60),
                "description" to item.title.take(500),
                "recurringId" to item.id.take(64),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(expRef, expPayload)
            batch.update(
                recRef,
                mapOf(
                    "nextDueDateIso" to nextDate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveCategoryBudget(budget: CategoryBudget): Result<Unit> {
        var path = "categoryBudgets"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("categoryBudgets")
            val isNew = budget.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(budget.id)
            path = docRef.path
            require(budget.limitAmount > 0.0)
            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "categoryId" to budget.categoryId.take(64),
                    "categoryName" to budget.categoryName.take(60),
                    "limitAmount" to budget.limitAmount,
                    "currency" to budget.currency,
                    "monthKey" to budget.monthKey,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "categoryId" to budget.categoryId.take(64),
                        "categoryName" to budget.categoryName.take(60),
                        "limitAmount" to budget.limitAmount,
                        "currency" to budget.currency,
                        "monthKey" to budget.monthKey,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun seedSampleData(currencyCode: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val today = DateUtils.todayIso()
            val yesterday = DateUtils.addDays(today, -1)
            val twoDaysAgo = DateUtils.addDays(today, -2)
            val fourDaysAgo = DateUtils.addDays(today, -4)

            val batch = db.batch()
            val shiftsCol = userDoc(uid).collection("workShifts")
            val sampleShifts = listOf(
                mapOf(
                    "userId" to uid, "dateIso" to today, "companyName" to "Main Job",
                    "startTime" to "08:00", "endTime" to "17:00", "overnight" to false,
                    "durationMinutes" to 540L, "payType" to WorkPayType.FIXED.name,
                    "rateOrFixedAmount" to 1500.0, "totalEarnings" to 1500.0,
                    "currency" to currencyCode, "notes" to "Regular weekday shift",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to uid, "dateIso" to today, "companyName" to "Evening Job",
                    "startTime" to "19:00", "endTime" to "23:00", "overnight" to false,
                    "durationMinutes" to 240L, "payType" to WorkPayType.FIXED.name,
                    "rateOrFixedAmount" to 1000.0, "totalEarnings" to 1000.0,
                    "currency" to currencyCode, "notes" to "Second job same day",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to uid, "dateIso" to yesterday, "companyName" to "Night Logistics",
                    "startTime" to "21:00", "endTime" to "03:00", "overnight" to true,
                    "durationMinutes" to 360L, "payType" to WorkPayType.HOURLY.name,
                    "rateOrFixedAmount" to 250.0, "totalEarnings" to 1500.0,
                    "currency" to currencyCode, "notes" to "Overnight shift",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to uid, "dateIso" to twoDaysAgo, "companyName" to "Main Job",
                    "startTime" to "08:30", "endTime" to "17:30", "overnight" to false,
                    "durationMinutes" to 540L, "payType" to WorkPayType.FIXED.name,
                    "rateOrFixedAmount" to 1500.0, "totalEarnings" to 1500.0,
                    "currency" to currencyCode,
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            sampleShifts.forEach { batch.set(shiftsCol.document(), it) }

            val expCol = userDoc(uid).collection("expenses")
            val sampleExpenses = listOf(
                mapOf(
                    "userId" to uid, "dateIso" to today, "amount" to 280.0, "currency" to currencyCode,
                    "categoryId" to "cat_food", "categoryName" to "Food", "description" to "Lunch & coffee",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to uid, "dateIso" to yesterday, "amount" to 140.0, "currency" to currencyCode,
                    "categoryId" to "cat_transport", "categoryName" to "Transportation / Travel", "description" to "Transit pass",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to uid, "dateIso" to fourDaysAgo, "amount" to 4500.0, "currency" to currencyCode,
                    "categoryId" to "cat_housing", "categoryName" to "Housing / Rent", "description" to "Monthly apartment rent",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            sampleExpenses.forEach { batch.set(expCol.document(), it) }

            val goalsCol = userDoc(uid).collection("savingsGoals")
            batch.set(goalsCol.document("sample_goal_1"), mapOf(
                "userId" to uid, "title" to "Emergency Reserve", "targetAmount" to 50000.0,
                "currentAmount" to 18500.0, "currency" to currencyCode,
                "targetDateIso" to DateUtils.addMonths(today, 6),
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))
            batch.set(goalsCol.document("sample_goal_2"), mapOf(
                "userId" to uid, "title" to "New Laptop", "targetAmount" to 30000.0,
                "currentAmount" to 12000.0, "currency" to currencyCode,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))

            val debtsCol = userDoc(uid).collection("debts")
            batch.set(debtsCol.document(), mapOf(
                "userId" to uid, "direction" to DebtDirection.OWED_TO_ME.name,
                "personName" to "Murat K.", "totalAmount" to 3000.0, "paidAmount" to 1000.0,
                "currency" to currencyCode, "dateIso" to fourDaysAgo,
                "dueDateIso" to DateUtils.addDays(today, 14), "status" to DebtStatus.PARTIAL.name,
                "note" to "Shared equipment split",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))
            batch.set(debtsCol.document(), mapOf(
                "userId" to uid, "direction" to DebtDirection.I_OWE.name,
                "personName" to "City Credit", "totalAmount" to 6000.0, "paidAmount" to 0.0,
                "currency" to currencyCode, "dateIso" to fourDaysAgo,
                "dueDateIso" to DateUtils.addDays(today, 20), "status" to DebtStatus.UNPAID.name,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))

            val recCol = userDoc(uid).collection("recurringExpenses")
            batch.set(recCol.document(), mapOf(
                "userId" to uid, "title" to "Home Fiber Internet", "amount" to 450.0,
                "currency" to currencyCode, "categoryId" to "cat_phone",
                "categoryName" to "Phone & Internet", "frequency" to RecurringFrequency.MONTHLY.name,
                "nextDueDateIso" to DateUtils.addDays(today, 5), "active" to true,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))

            val budgetCol = userDoc(uid).collection("categoryBudgets")
            batch.set(budgetCol.document(), mapOf(
                "userId" to uid, "categoryId" to "cat_food", "categoryName" to "Food",
                "limitAmount" to 4000.0, "currency" to currencyCode, "monthKey" to "ALL",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))
            batch.set(budgetCol.document(), mapOf(
                "userId" to uid, "categoryId" to "cat_housing", "categoryName" to "Housing / Rent",
                "limitAmount" to 5000.0, "currency" to currencyCode, "monthKey" to "ALL",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllUserData(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val collections = listOf(
                "workShifts", "expenses", "savingsGoals",
                "savingsTransfers", "debts", "recurringExpenses", "categoryBudgets"
            )
            for (colName in collections) {
                val snap = userDoc(uid).collection(colName).get().await()
                val chunks = snap.documents.chunked(400)
                for (chunk in chunks) {
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackup(backup: BackupPayload): Result<Int> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            var count = 0

            val writes = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

            backup.profile?.let { p ->
                val profileExists = userDoc(uid).get().await().exists()
                val baseProfile = mutableMapOf<String, Any>(
                    "displayName" to p.displayName.ifBlank { "Finance User" }.take(100),
                    "primaryCurrency" to p.primaryCurrency,
                    "monthlyIncomeGoal" to p.monthlyIncomeGoal,
                    "monthlyBudgetLimit" to p.monthlyBudgetLimit,
                    "rateTryToUsd" to p.rateTryToUsd,
                    "rateTmtToUsd" to p.rateTmtToUsd,
                    "rateEurToUsd" to p.rateEurToUsd,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (profileExists) {
                    userDoc(uid).update(baseProfile).await()
                } else {
                    baseProfile["userId"] = uid
                    baseProfile["createdAt"] = FieldValue.serverTimestamp()
                    userDoc(uid).set(baseProfile).await()
                }
            }

            fun addDocs(col: String, items: List<Map<String, Any>>) {
                val colRef = userDoc(uid).collection(col)
                items.forEach { raw ->
                    val clean = raw.toMutableMap()
                    clean.remove("id")
                    clean["userId"] = uid
                    clean["createdAt"] = FieldValue.serverTimestamp()
                    clean["updatedAt"] = FieldValue.serverTimestamp()
                    writes += colRef.document() to clean
                    count++
                }
            }

            addDocs("categories", backup.categories)
            addDocs("workShifts", backup.workShifts)
            addDocs("expenses", backup.expenses)
            addDocs("savingsGoals", backup.savingsGoals)
            addDocs("savingsTransfers", backup.savingsTransfers)
            addDocs("debts", backup.debts)
            addDocs("recurringExpenses", backup.recurringExpenses)
            addDocs("categoryBudgets", backup.categoryBudgets)

            writes.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, payload) -> batch.set(ref, payload) }
                batch.commit().await()
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun mergeLocalSnapshotToCloud(snapshot: LocalFinanceSnapshot): Result<Int> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            var syncedCount = 0

            if (snapshot.hasCustomizedProfile) {
                val p = snapshot.profile
                val profileUpdates = mutableMapOf<String, Any>(
                    "primaryCurrency" to p.primaryCurrency,
                    "monthlyIncomeGoal" to p.monthlyIncomeGoal,
                    "monthlyBudgetLimit" to p.monthlyBudgetLimit,
                    "rateTryToUsd" to p.rateTryToUsd,
                    "rateTmtToUsd" to p.rateTmtToUsd,
                    "rateEurToUsd" to p.rateEurToUsd,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val customName = p.displayName.trim().take(100)
                if (customName.isNotEmpty() && customName != "Finance User") {
                    profileUpdates["displayName"] = customName
                }
                userDoc(uid).update(profileUpdates).await()
            }

            suspend fun existingIds(col: String): Set<String> =
                userDoc(uid).collection(col).get().await().documents.map { it.id }.toSet()

            val writes = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

            val existingCats = existingIds("categories")
            val catsCol = userDoc(uid).collection("categories")
            snapshot.categories.filter { it.id.isNotBlank() && it.id !in existingCats }.forEach { c ->
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "name" to c.name.trim().take(60).ifEmpty { "Category" },
                    "iconKey" to c.iconKey.take(40),
                    "colorHex" to c.colorHex,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                c.defaultKey?.take(40)?.let { data["defaultKey"] = it }
                writes += catsCol.document(c.id) to data
                syncedCount++
            }

            val existingShifts = existingIds("workShifts")
            val shiftsCol = userDoc(uid).collection("workShifts")
            snapshot.workShifts.filter { it.id.isNotBlank() && it.id !in existingShifts }.forEach { s ->
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "dateIso" to s.dateIso,
                    "companyName" to s.companyName.trim().take(100).ifEmpty { "Job" },
                    "startTime" to s.startTime,
                    "endTime" to s.endTime,
                    "overnight" to s.overnight,
                    "durationMinutes" to s.durationMinutes.coerceIn(1L, 1440L),
                    "payType" to s.payType,
                    "rateOrFixedAmount" to s.rateOrFixedAmount.coerceAtLeast(0.0),
                    "totalEarnings" to s.totalEarnings.coerceAtLeast(0.0),
                    "currency" to s.currency,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                s.notes?.trim()?.take(500)?.ifEmpty { null }?.let { data["notes"] = it }
                writes += shiftsCol.document(s.id) to data
                syncedCount++
            }

            val existingExp = existingIds("expenses")
            val expCol = userDoc(uid).collection("expenses")
            snapshot.expenses.filter { it.id.isNotBlank() && it.id !in existingExp && it.amount > 0.0 }.forEach { e ->
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "dateIso" to e.dateIso,
                    "amount" to e.amount,
                    "currency" to e.currency,
                    "categoryId" to e.categoryId.take(64),
                    "categoryName" to e.categoryName.trim().take(60).ifEmpty { "Other" },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                e.description?.trim()?.take(500)?.ifEmpty { null }?.let { data["description"] = it }
                e.recurringId?.trim()?.take(64)?.ifEmpty { null }?.let { data["recurringId"] = it }
                writes += expCol.document(e.id) to data
                syncedCount++
            }

            val existingGoals = existingIds("savingsGoals")
            val goalsCol = userDoc(uid).collection("savingsGoals")
            snapshot.savingsGoals.filter { it.id.isNotBlank() && it.id !in existingGoals && it.targetAmount > 0.0 }.forEach { g ->
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "title" to g.title.trim().take(80).ifEmpty { "Goal" },
                    "targetAmount" to g.targetAmount,
                    "currentAmount" to g.currentAmount.coerceAtLeast(0.0),
                    "currency" to g.currency,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                g.targetDateIso?.trim()?.take(10)?.ifEmpty { null }?.let { data["targetDateIso"] = it }
                writes += goalsCol.document(g.id) to data
                syncedCount++
            }

            val existingTransfers = existingIds("savingsTransfers")
            val transfersCol = userDoc(uid).collection("savingsTransfers")
            snapshot.savingsTransfers.filter { it.id.isNotBlank() && it.id !in existingTransfers && it.amount > 0.0 }.forEach { t ->
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "goalId" to t.goalId.take(64),
                    "goalTitle" to t.goalTitle.trim().take(80).ifEmpty { "Goal" },
                    "amount" to t.amount,
                    "currency" to t.currency,
                    "dateIso" to t.dateIso,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                t.note?.trim()?.take(300)?.ifEmpty { null }?.let { data["note"] = it }
                writes += transfersCol.document(t.id) to data
                syncedCount++
            }

            val existingDebts = existingIds("debts")
            val debtsCol = userDoc(uid).collection("debts")
            snapshot.debts.filter { it.id.isNotBlank() && it.id !in existingDebts && it.totalAmount > 0.0 }.forEach { d ->
                val safePaid = d.paidAmount.coerceIn(0.0, d.totalAmount)
                val status = when {
                    safePaid >= d.totalAmount - 0.005 -> DebtStatus.PAID.name
                    safePaid > 0.005 -> DebtStatus.PARTIAL.name
                    else -> DebtStatus.UNPAID.name
                }
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "direction" to d.direction,
                    "personName" to d.personName.trim().take(100).ifEmpty { "Person" },
                    "totalAmount" to d.totalAmount,
                    "paidAmount" to safePaid,
                    "currency" to d.currency,
                    "dateIso" to d.dateIso,
                    "status" to status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                d.dueDateIso?.trim()?.take(10)?.ifEmpty { null }?.let { data["dueDateIso"] = it }
                d.note?.trim()?.take(500)?.ifEmpty { null }?.let { data["note"] = it }
                writes += debtsCol.document(d.id) to data
                syncedCount++
            }

            val existingRec = existingIds("recurringExpenses")
            val recCol = userDoc(uid).collection("recurringExpenses")
            snapshot.recurringExpenses.filter { it.id.isNotBlank() && it.id !in existingRec && it.amount > 0.0 }.forEach { r ->
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "title" to r.title.trim().take(100).ifEmpty { "Bill" },
                    "amount" to r.amount,
                    "currency" to r.currency,
                    "categoryId" to r.categoryId.take(64),
                    "categoryName" to r.categoryName.take(60),
                    "frequency" to r.frequency,
                    "nextDueDateIso" to r.nextDueDateIso,
                    "active" to r.active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                r.note?.trim()?.take(300)?.ifEmpty { null }?.let { data["note"] = it }
                writes += recCol.document(r.id) to data
                syncedCount++
            }

            val existingBudgets = existingIds("categoryBudgets")
            val budgetsCol = userDoc(uid).collection("categoryBudgets")
            snapshot.categoryBudgets.filter { it.id.isNotBlank() && it.id !in existingBudgets && it.limitAmount > 0.0 }.forEach { b ->
                val data = mapOf<String, Any>(
                    "userId" to uid,
                    "categoryId" to b.categoryId.take(64),
                    "categoryName" to b.categoryName.take(60),
                    "limitAmount" to b.limitAmount,
                    "currency" to b.currency,
                    "monthKey" to b.monthKey,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                writes += budgetsCol.document(b.id) to data
                syncedCount++
            }

            writes.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, payload) -> batch.set(ref, payload) }
                batch.commit().await()
            }

            Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
