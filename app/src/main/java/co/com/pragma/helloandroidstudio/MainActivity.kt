package co.com.pragma.helloandroidstudio

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.robinhood.spark.SparkView
import com.robinhood.ticker.TickerUtils
import com.robinhood.ticker.TickerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


private const val BASE_URL = "https://api.covidtracking.com/v1/"
private const val TAG = "MainActivity"
private const val ALL_STATES = "All (Nationwide)"

class MainActivity : AppCompatActivity() {
    private lateinit var currentShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var adapterArray: ArrayAdapter<String>
    private lateinit var nationalDailyData: List<CovidData>
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var tickerView: TickerView
    private lateinit var tvDateLabel: TextView
    private lateinit var radioButtonPositive: RadioButton
    private lateinit var radioButtonMax: RadioButton
    private lateinit var sparkView: SparkView
    private lateinit var radioGroupTimeSelection: RadioGroup
    private lateinit var radioGroupMetricSelection: RadioGroup
    private lateinit var spStates: Spinner
    var stateList = listOf<String>("Updating")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        tickerView = findViewById(R.id.tickerView)
        tvDateLabel = findViewById(R.id.tvDateLabel)
        radioButtonPositive = findViewById(R.id.radioButtonPositive)
        radioButtonMax = findViewById(R.id.radioButtonMax)
        sparkView = findViewById(R.id.sparkView)
        radioGroupTimeSelection = findViewById(R.id.radioGroupTimeSelection)
        radioGroupMetricSelection = findViewById(R.id.radioGroupMetricSelection)
        spStates = findViewById(R.id.spStates)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService = retrofit.create(CovidService::class.java)

        adapterArray = ArrayAdapter(this, android.R.layout.simple_spinner_item, stateList)
        spStates.adapter = adapterArray
        // fetch National Data
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.i(TAG, "onResponse $response")
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.w(TAG, "Did nit receive a valid response body")
                    return
                }
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "Update graph with national data")


                updateDisplayWithData(nationalDailyData)

            }


            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

        })
        // Fetch the state

        covidService.getStatesData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.i(TAG, "onResponse $response")
                val stateData = response.body()
                if (stateData == null) {
                    Log.w(TAG, "Did nit receive a valid response body")
                    return
                }
                perStateDailyData = stateData.reversed().groupBy {
                    it.state
                }
                Log.i(TAG, "Update spinner with state names")
                // Update spinner with state names
                updateSpinnerWithStateData(perStateDailyData.keys)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

        })
    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0, ALL_STATES)
        stateList = stateAbbreviationList;
        adapterArray =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, stateList)
        spStates.adapter = adapterArray
        spStates.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val selectedState = stateList[p2]
                val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
                updateDisplayWithData(selectedData)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                Toast.makeText(this@MainActivity, "Nothing selected", Toast.LENGTH_LONG).show()
            }
        }
        // add state list as data source for the spinner

    }

    private fun setupEventListeners() {
        tickerView.setCharacterLists(TickerUtils.provideNumberList())


        // add a Listener for the user scrubbing on the chart
        sparkView.isScrubEnabled = true
        sparkView.setScrubListener { itemData ->
            if (itemData is CovidData) {
                updateInfoForDate(itemData)
            }
        }
        // respond to radiobutton select events
        radioGroupTimeSelection.setOnCheckedChangeListener { _, checkedId ->
            adapter.daysAgo = when (checkedId) {
                R.id.radioButtonWeek -> TimeScale.WEEK
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        radioGroupMetricSelection.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                else -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        // update color for the chart
        val colorRes = when (metric) {

            Metric.POSITIVE -> R.color.colorPositive
            Metric.DEATH -> R.color.colorDeath
            else -> R.color.colorNegative

        }


        @ColorInt val colorInt = ContextCompat.getColor(this, colorRes)
        sparkView.lineColor = colorInt
        tickerView.setTextColor(colorInt)

        // Update metric on the adapter
        adapter.metric = metric
        adapter.notifyDataSetChanged()
        // reset the number and the date shown in the bottom text views
        updateInfoForDate(currentShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentShownData = dailyData
        // create a new SparkerAdapter with the data
        adapter = CovidSparkAdapter(dailyData)
        sparkView.adapter = adapter
        // Update radiovbuttons to select the positive cases and max time by default
        // Display metric for the most recent date
        radioButtonPositive.isChecked = true
        radioButtonMax.isChecked = true
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when (adapter.metric) {
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
        tickerView.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)
    }
}
