package co.com.pragma.helloandroidstudio
import com.google.gson.annotations.SerializedName
import java.util.*

data class CovidData (
   @SerializedName( value = "dateChecked") val dateChecked: Date,
   @SerializedName( value = "positiveIncrease") val positiveIncrease:Int,
   @SerializedName( value = "negativeIncrease") val negativeIncrease:Int,
   @SerializedName( value = "deathIncrease") val deathIncrease:Int,
   @SerializedName( value = "state") val state:String,
        )