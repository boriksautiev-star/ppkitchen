package com.ppkitchen.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Client::class, Order::class, Expense::class,
        CashDeposit::class, Courier::class, OrderCourierAssignment::class,
        Cook::class, Ingredient::class, IngredientCategory::class, Dish::class, DishIngredient::class,
        FoodReference::class, DishCategory::class,
        MenuTemplate::class, MenuDay::class, MenuDayDish::class
    ],
    version = 31,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun orderDao(): OrderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun cashDepositDao(): CashDepositDao
    abstract fun courierDao(): CourierDao
    abstract fun assignmentDao(): OrderCourierAssignmentDao
    abstract fun cookDao(): CookDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun ingredientCategoryDao(): IngredientCategoryDao
    abstract fun dishDao(): DishDao
    abstract fun dishIngredientDao(): DishIngredientDao
    abstract fun foodReferenceDao(): FoodReferenceDao
    abstract fun dishCategoryDao(): DishCategoryDao
    abstract fun menuTemplateDao(): MenuTemplateDao
    abstract fun menuDayDao(): MenuDayDao
    abstract fun menuDayDishDao(): MenuDayDishDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cash_deposits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        comment TEXT NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN discountPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN dailyPayment INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN amountPerDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN deliveryDaysOfWeek TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN isOpenEnded INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN discountFromDate TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN discountHistory TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE orders 
                    SET discountHistory = 
                        CASE 
                            WHEN discountFromDate = '' THEN deliveryStart || ':' || discountPercent
                            ELSE discountFromDate || ':' || discountPercent
                        END
                    WHERE discountPercent > 0
                """)
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS couriers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        comment TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                """)
                db.execSQL("ALTER TABLE orders ADD COLUMN courierId INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS order_courier_assignments (
                        orderId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        courierId INTEGER NOT NULL,
                        PRIMARY KEY(orderId, date)
                    )
                """)
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cooks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL DEFAULT '',
                        rateWeekday INTEGER NOT NULL DEFAULT 0,
                        rateWeekend INTEGER NOT NULL DEFAULT 0,
                        workDays TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7',
                        active INTEGER NOT NULL DEFAULT 1,
                        comment TEXT NOT NULL DEFAULT ''
                    )
                """)
                db.execSQL("""
                    UPDATE expenses SET category = 'Зарплата (ручная)' WHERE category = 'Зарплата'
                """)
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cooks_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL DEFAULT '',
                        rateMon INTEGER NOT NULL DEFAULT 0,
                        rateTue INTEGER NOT NULL DEFAULT 0,
                        rateWed INTEGER NOT NULL DEFAULT 0,
                        rateThu INTEGER NOT NULL DEFAULT 0,
                        rateFri INTEGER NOT NULL DEFAULT 0,
                        rateSat INTEGER NOT NULL DEFAULT 0,
                        rateSun INTEGER NOT NULL DEFAULT 0,
                        active INTEGER NOT NULL DEFAULT 1,
                        comment TEXT NOT NULL DEFAULT ''
                    )
                """)
                db.execSQL("""
                    INSERT INTO cooks_new (id, name, phone,
                        rateMon, rateTue, rateWed, rateThu, rateFri, rateSat, rateSun,
                        active, comment)
                    SELECT 
                        id, name, phone,
                        CASE WHEN ',' || workDays || ',' LIKE '%,1,%' THEN rateWeekday ELSE 0 END,
                        CASE WHEN ',' || workDays || ',' LIKE '%,2,%' THEN rateWeekday ELSE 0 END,
                        CASE WHEN ',' || workDays || ',' LIKE '%,3,%' THEN rateWeekday ELSE 0 END,
                        CASE WHEN ',' || workDays || ',' LIKE '%,4,%' THEN rateWeekday ELSE 0 END,
                        CASE WHEN ',' || workDays || ',' LIKE '%,5,%' THEN rateWeekday ELSE 0 END,
                        CASE WHEN ',' || workDays || ',' LIKE '%,6,%' THEN rateWeekend ELSE 0 END,
                        CASE WHEN ',' || workDays || ',' LIKE '%,7,%' THEN rateWeekend ELSE 0 END,
                        active, comment
                    FROM cooks
                """)
                db.execSQL("DROP TABLE cooks")
                db.execSQL("ALTER TABLE cooks_new RENAME TO cooks")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN time TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN skippedDates TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN protein INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN fat INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN carbs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clients ADD COLUMN restrictions TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ingredients (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        pricePerKg REAL NOT NULL,
                        unit TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dishes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        calories INTEGER NOT NULL,
                        protein INTEGER NOT NULL,
                        fat INTEGER NOT NULL,
                        carbs INTEGER NOT NULL,
                        category TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dish_ingredients (
                        dishId INTEGER NOT NULL,
                        ingredientId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        PRIMARY KEY(dishId, ingredientId)
                    )
                """)
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredients ADD COLUMN category TEXT NOT NULL DEFAULT 'Прочее'")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ingredient_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO ingredient_categories (name) VALUES 
                    ('Мясо'), ('Рыба'), ('Овощи'), ('Фрукты'), ('Крупы'), ('Молочка'), ('Специи'), ('Соусы'), ('Прочее')
                """)
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredients ADD COLUMN calories REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE ingredients ADD COLUMN protein REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE ingredients ADD COLUMN fat REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE ingredients ADD COLUMN carbs REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS food_reference (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        calories REAL NOT NULL,
                        protein REAL NOT NULL,
                        fat REAL NOT NULL,
                        carbs REAL NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dish_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO dish_categories (name) VALUES 
                    ('Завтрак'), ('Обед'), ('Ужин'), ('Перекус'), ('Напиток'), ('Прочее')
                """)
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_reference ADD COLUMN source TEXT NOT NULL DEFAULT 'builtin'")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS menu_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        cycleLength INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS menu_days (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateId INTEGER NOT NULL,
                        dayNumber INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS menu_day_dishes (
                        menuDayId INTEGER NOT NULL,
                        dishId INTEGER NOT NULL,
                        PRIMARY KEY(menuDayId, dishId)
                    )
                """)
                db.execSQL("ALTER TABLE orders ADD COLUMN menuTemplateId INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredients ADD COLUMN wastePercent REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_reference ADD COLUMN wastePercent REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredients ADD COLUMN heatLossPercent REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dish_ingredients ADD COLUMN cookingMethod TEXT NOT NULL DEFAULT 'Сырой'")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ppkitchen.db"
                )
                    .addMigrations(
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                        MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                        MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                        MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26,
                        MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
                        MIGRATION_29_30, MIGRATION_30_31
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }

        fun reset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}