package com.pomoremote.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pomoremote.MainActivity
import com.pomoremote.R
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = HistoryAdapter()
        recyclerView.adapter = adapter

        fetchHistory()
    }

    private fun fetchHistory() {
        val activity = activity as? MainActivity ?: return
        val ip = activity.prefs.laptopIp
        val port = activity.prefs.laptopPort
        val url = "http://$ip:$port/api/history"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to load history: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val type = object : TypeToken<Map<String, DayEntry>>() {}.type
                    val historyMap: Map<String, DayEntry> = gson.fromJson(json, type)

                    val historyList = historyMap.entries.map {
                        HistoryItem(it.key, it.value)
                    }.sortedByDescending { it.date }

                    Handler(Looper.getMainLooper()).post {
                        adapter.submitList(historyList)
                    }
                }
            }
        })
    }
}

data class DayEntry(
    val completed: Int,
    val work_minutes: Int,
    val break_minutes: Int
)

data class HistoryItem(val date: String, val entry: DayEntry)

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    private var list: List<HistoryItem> = emptyList()

    fun submitList(newList: List<HistoryItem>) {
        list = newList
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvFocus: TextView = view.findViewById(R.id.tvFocusTime)
        val tvBreak: TextView = view.findViewById(R.id.tvBreakTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvDate.text = item.date
        holder.tvFocus.text = "${item.entry.work_minutes}m"
        holder.tvBreak.text = "${item.entry.break_minutes}m"
    }

    override fun getItemCount() = list.size
}