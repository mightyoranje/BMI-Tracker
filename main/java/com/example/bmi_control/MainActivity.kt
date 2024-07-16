package com.example.bmi_control

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.bmi_control.ui.theme.BMIcontrolTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var bmiViewModel: BMIViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = BMIRepository(BMIDatabase.getDatabase(this).bmiDao())
        bmiViewModel = ViewModelProvider(this, BMIViewModelFactory(repository))[BMIViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            BMIcontrolTheme {
                Main(bmiViewModel)
            }
        }
    }
}

@Database(entities = [BMIEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BMIDatabase : RoomDatabase() {
    abstract fun bmiDao(): BMIDao

    companion object {
        @Volatile
        private var INSTANCE: BMIDatabase? = null

        fun getDatabase(context: Context): BMIDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BMIDatabase::class.java,
                    "bmi_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

@Entity(tableName = "bmi_entries")
data class BMIEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bmi: Double,
    val date: Date = Date()
)

@Dao
interface BMIDao {
    @Insert
    suspend fun insert(bmiEntry: BMIEntry)

    @Query("SELECT * FROM bmi_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<BMIEntry>>

    @Delete
    suspend fun delete(bmiEntry: BMIEntry)
}

class BMIRepository(private val bmiDao: BMIDao) {
    val allEntries: Flow<List<BMIEntry>> = bmiDao.getAllEntries()

    suspend fun insert(bmiEntry: BMIEntry) {
        bmiDao.insert(bmiEntry)
    }

    suspend fun delete(bmiEntry: BMIEntry) {
        bmiDao.delete(bmiEntry)
    }

}

class BMIViewModel(private val repository: BMIRepository) : ViewModel() {
    val allEntries: Flow<List<BMIEntry>> = repository.allEntries

    fun insertBMI(bmi: Double) {
        viewModelScope.launch {
            repository.insert(BMIEntry(bmi = bmi))
        }
    }

    fun deleteEntry(entry: BMIEntry) {  // Changed from deleteBMIEntry to deleteEntry
        viewModelScope.launch {
            repository.delete(entry)
        }
    }
}


class BMIViewModelFactory(private val repository: BMIRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BMIViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BMIViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


@Composable
fun Main(bmiViewModel: BMIViewModel) {
    val navController = rememberNavController()
    val items = listOf("home", "history")

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") { HomeScreen(bmiViewModel) }
            composable("history") { HistoryScreen(bmiViewModel) }
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            items.forEach { item ->   
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (item == "home") Icons.Filled.Home else Icons.AutoMirrored.Filled.List,
                            contentDescription = null
                        )
                    },
                    label = { Text(item.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }) },
                    selected = currentRoute == item,
                    onClick = {
                        navController.navigate(item) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(bmiViewModel: BMIViewModel)  {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var text1 by remember { mutableStateOf("") }
        OutlinedTextField(
            value = text1,
            onValueChange = { newText -> text1 = newText },
            label = { Text(text = "cm") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = "") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        var text2 by remember { mutableStateOf("") }
        OutlinedTextField(
            value = text2,
            onValueChange = { newText -> text2 = newText },
            label = { Text(text = "kg") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Favorite, contentDescription = "") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        val height = text1.toDoubleOrNull() ?: 0.0
        val weight = text2.toDoubleOrNull() ?: 0.0
        val result = calculatebmi(y = height, x = weight)
        Text(text = "BMI: $result", fontSize = 20.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            val bmi = calculatebmi(height, weight).toDoubleOrNull() ?: return@Button
            bmiViewModel.insertBMI(bmi)
        }) {
            Text(text = "Log BMI")
        }

    }
}

@Composable
fun HistoryScreen(bmiViewModel: BMIViewModel) {
    val entries by bmiViewModel.allEntries.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(entries) { entry ->
            BMIEntryItem(
                entry = entry,
                onDelete = { bmiViewModel.deleteEntry(entry) }
            )
            HorizontalDivider(thickness = 3.dp, color = Color.Gray)
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun BMIEntryItem(entry: BMIEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("BMI: ${String.format("%.2f", entry.bmi)}")
            Text(
                "Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(entry.date)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete entry")
        }
    }
}

fun calculatebmi(y: Double, x: Double): String {
    val thing = (x * 10000) / (y * y)
    return String.format("%.2f", thing)

}
