package com.ppkitchen.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class CalorieStat(
    val calories: Int,
    val count: Int
)

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY firstName, lastName")
    fun getAll(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE id = :id")
    fun getById(id: Long): Flow<Client?>

    @Insert
    suspend fun insert(client: Client): Long

    @Update
    suspend fun update(client: Client)

    @Delete
    suspend fun delete(client: Client)
}

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders WHERE deliveryStart <= :date AND deliveryEnd >= :date ORDER BY id")
    fun getForDelivery(date: String): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE clientId = :clientId ORDER BY date DESC")
    fun getByClient(clientId: Long): Flow<List<Order>>

    @Query("SELECT * FROM orders ORDER BY deliveryStart DESC")
    fun getAll(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE dailyPayment = 1")
    fun getDailyOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE date >= :start AND date <= :end ORDER BY date, id")
    fun ordersBetween(start: String, end: String): Flow<List<Order>>

    @Insert
    suspend fun insert(order: Order)

    @Update
    suspend fun update(order: Order)

    @Delete
    suspend fun delete(order: Order)

    @Query("SELECT SUM(amount) FROM orders WHERE date >= :start AND date <= :end AND paid = 1 AND dailyPayment = 0")
    fun revenueBetween(start: String, end: String): Flow<Int?>

    @Query("SELECT COUNT(*) FROM orders WHERE date >= :start AND date <= :end")
    fun ordersCountBetween(start: String, end: String): Flow<Int>

    @Query("SELECT COUNT(DISTINCT clientId) FROM orders WHERE date >= :start AND date <= :end")
    fun uniqueClientsBetween(start: String, end: String): Flow<Int>

    @Query("""
        SELECT calories AS calories, COUNT(*) AS count 
        FROM orders 
        WHERE date >= :start AND date <= :end 
        GROUP BY calories 
        ORDER BY calories
    """)
    fun caloriesBreakdown(start: String, end: String): Flow<List<CalorieStat>>

    @Query("SELECT COUNT(*) FROM orders WHERE clientId = :clientId")
    fun ordersCountByClient(clientId: Long): Flow<Int>

    @Query("SELECT SUM(amount) FROM orders WHERE clientId = :clientId AND paid = 1 AND dailyPayment = 0")
    fun totalPaidByClient(clientId: Long): Flow<Int?>

    @Query("SELECT SUM(amount) FROM orders WHERE clientId = :clientId AND paid = 0 AND dailyPayment = 0")
    fun totalUnpaidByClient(clientId: Long): Flow<Int?>
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date >= :start AND date <= :end ORDER BY date, id")
    fun expensesListBetween(start: String, end: String): Flow<List<Expense>>

    @Insert
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT SUM(amount) FROM expenses WHERE date >= :start AND date <= :end")
    fun expensesBetween(start: String, end: String): Flow<Int?>

    @Query("SELECT SUM(amount) FROM expenses WHERE date >= :start AND date <= :end AND category = :category")
    fun expensesByCategoryBetween(start: String, end: String, category: String): Flow<Int?>
}

@Dao
interface CashDepositDao {
    @Query("SELECT * FROM cash_deposits ORDER BY date DESC, id DESC")
    fun getAll(): Flow<List<CashDeposit>>

    @Insert
    suspend fun insert(deposit: CashDeposit)

    @Update
    suspend fun update(deposit: CashDeposit)

    @Delete
    suspend fun delete(deposit: CashDeposit)

    @Query("SELECT SUM(amount) FROM cash_deposits WHERE date >= :start AND date <= :end")
    fun depositsBetween(start: String, end: String): Flow<Int?>

    @Query("SELECT SUM(amount) FROM cash_deposits")
    fun depositsTotal(): Flow<Int?>
}

@Dao
interface CourierDao {
    @Query("SELECT * FROM couriers ORDER BY name")
    fun getAll(): Flow<List<Courier>>

    @Query("SELECT * FROM couriers WHERE status = 'активен' ORDER BY name")
    fun getActive(): Flow<List<Courier>>

    @Query("SELECT * FROM couriers WHERE id = :id")
    fun getById(id: Long): Flow<Courier?>

    @Insert
    suspend fun insert(courier: Courier): Long

    @Update
    suspend fun update(courier: Courier)

    @Delete
    suspend fun delete(courier: Courier)
}

@Dao
interface OrderCourierAssignmentDao {
    @Query("SELECT * FROM order_courier_assignments WHERE date = :date")
    fun getForDate(date: String): Flow<List<OrderCourierAssignment>>

    @Query("SELECT * FROM order_courier_assignments WHERE orderId = :orderId ORDER BY date DESC")
    fun getForOrder(orderId: Long): Flow<List<OrderCourierAssignment>>

    @Query("SELECT * FROM order_courier_assignments WHERE courierId = :courierId AND date = :date")
    fun getByCourierAndDate(courierId: Long, date: String): Flow<List<OrderCourierAssignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: OrderCourierAssignment)

    @Query("DELETE FROM order_courier_assignments WHERE orderId = :orderId AND date = :date")
    suspend fun delete(orderId: Long, date: String)

    @Query("DELETE FROM order_courier_assignments WHERE courierId = :courierId AND date = :date")
    suspend fun deleteByCourierAndDate(courierId: Long, date: String)
}