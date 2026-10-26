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

    @Query("SELECT COUNT(*) FROM expenses WHERE date = :date AND category = :category AND comment = :comment")
    suspend fun countByDateCategoryComment(date: String, category: String, comment: String): Int
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

@Dao
interface CookDao {
    @Query("SELECT * FROM cooks ORDER BY active DESC, name")
    fun getAll(): Flow<List<Cook>>

    @Query("SELECT * FROM cooks WHERE active = 1 ORDER BY name")
    fun getActive(): Flow<List<Cook>>

    @Query("SELECT * FROM cooks WHERE id = :id")
    fun getById(id: Long): Flow<Cook?>

    @Insert
    suspend fun insert(cook: Cook): Long

    @Update
    suspend fun update(cook: Cook)

    @Delete
    suspend fun delete(cook: Cook)
}

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients ORDER BY name")
    fun getAll(): Flow<List<Ingredient>>

    @Insert
    suspend fun insert(ingredient: Ingredient): Long

    @Update
    suspend fun update(ingredient: Ingredient)

    @Delete
    suspend fun delete(ingredient: Ingredient)
}

@Dao
interface IngredientCategoryDao {
    @Query("SELECT * FROM ingredient_categories ORDER BY name")
    fun getAll(): Flow<List<IngredientCategory>>

    @Insert
    suspend fun insert(category: IngredientCategory): Long

    @Update
    suspend fun update(category: IngredientCategory)

    @Delete
    suspend fun delete(category: IngredientCategory)

    @Query("UPDATE ingredients SET category = :newName WHERE category = :oldName")
    suspend fun renameCategoryInIngredients(oldName: String, newName: String)
}

@Dao
interface DishDao {
    @Query("SELECT * FROM dishes ORDER BY category, name")
    fun getAll(): Flow<List<Dish>>

    @Insert
    suspend fun insert(dish: Dish): Long

    @Update
    suspend fun update(dish: Dish)

    @Delete
    suspend fun delete(dish: Dish)
}

@Dao
interface DishIngredientDao {
    @Query("SELECT * FROM dish_ingredients")
    fun getAll(): Flow<List<DishIngredient>>

    @Query("SELECT * FROM dish_ingredients WHERE dishId = :dishId")
    fun getForDish(dishId: Long): Flow<List<DishIngredient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dishIngredient: DishIngredient)

    @Query("DELETE FROM dish_ingredients WHERE dishId = :dishId")
    suspend fun deleteForDish(dishId: Long)
}

@Dao
interface FoodReferenceDao {
    @Query("SELECT * FROM food_reference WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 20")
    suspend fun search(query: String): List<FoodReference>

    @Query("SELECT * FROM food_reference WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): FoodReference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FoodReference>)

    @Insert
    suspend fun insert(item: FoodReference): Long

    @Query("SELECT COUNT(*) FROM food_reference")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM food_reference WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("DELETE FROM food_reference WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT * FROM food_reference ORDER BY name")
    suspend fun getAllList(): List<FoodReference>
}

@Dao
interface DishCategoryDao {
    @Query("SELECT * FROM dish_categories ORDER BY name")
    fun getAll(): Flow<List<DishCategory>>

    @Insert
    suspend fun insert(category: DishCategory): Long

    @Update
    suspend fun update(category: DishCategory)

    @Delete
    suspend fun delete(category: DishCategory)

    @Query("UPDATE dishes SET category = :newName WHERE category = :oldName")
    suspend fun renameCategoryInDishes(oldName: String, newName: String)
}

@Dao
interface MenuTemplateDao {
    @Query("SELECT * FROM menu_templates ORDER BY id")
    fun getAll(): Flow<List<MenuTemplate>>

    @Insert
    suspend fun insert(template: MenuTemplate): Long

    @Update
    suspend fun update(template: MenuTemplate)

    @Delete
    suspend fun delete(template: MenuTemplate)
}

@Dao
interface MenuDayDao {
    @Query("SELECT * FROM menu_days WHERE templateId = :templateId ORDER BY dayNumber")
    fun getDaysForTemplate(templateId: Long): Flow<List<MenuDay>>

    // ВОТ ЭТОТ МЕТОД МЫ ДОБАВИЛИ:
    @Query("SELECT * FROM menu_days")
    suspend fun getAllOnce(): List<MenuDay>

    @Insert
    suspend fun insert(day: MenuDay): Long

    @Delete
    suspend fun delete(day: MenuDay)
}

@Dao
interface MenuDayDishDao {
    @Query("SELECT * FROM menu_day_dishes WHERE menuDayId = :menuDayId")
    fun getDishesForDay(menuDayId: Long): Flow<List<MenuDayDish>>

    // ВОТ ЭТОТ МЕТОД МЫ ДОБАВИЛИ:
    @Query("SELECT * FROM menu_day_dishes")
    suspend fun getAllOnce(): List<MenuDayDish>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: MenuDayDish)

    @Query("DELETE FROM menu_day_dishes WHERE menuDayId = :menuDayId")
    suspend fun deleteForDay(menuDayId: Long)
}
@Dao
interface RationDao {
    @Query("SELECT * FROM rations ORDER BY calories")
    fun getAll(): Flow<List<Ration>>

    @Insert
    suspend fun insert(ration: Ration): Long

    @Update
    suspend fun update(ration: Ration)

    @Delete
    suspend fun delete(ration: Ration)
}