package com.example.cfstats

import android.app.Activity
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import android.graphics.Color
import android.view.ViewGroup.LayoutParams

class MainActivity : Activity() {

    private lateinit var resultTextView: TextView
    private lateinit var inputHandle: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Программное создание интерфейса (чтобы избежать XML-верстки с телефона)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        inputHandle = EditText(this).apply {
            hint = "Введите хэндл (например, tourist)"
        }
        
        val btnFetch = Button(this).apply {
            text = "Получить статистику"
            setOnClickListener { fetchStats(inputHandle.text.toString()) }
        }

        val scrollView = ScrollView(this)
        resultTextView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.BLACK)
        }
        
        scrollView.addView(resultTextView)
        
        layout.addView(inputHandle)
        layout.addView(btnFetch)
        layout.addView(scrollView)

        setContentView(layout)
    }

    private fun fetchStats(handle: String) {
        if (handle.isBlank()) return
        resultTextView.text = "Загрузка данных для $handle..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://codeforces.com/api/user.status?handle=$handle"
                val response = URL(url).readText()
                val json = JSONObject(response)

                if (json.getString("status") == "OK") {
                    val result = json.getJSONArray("result")
                    val parsedData = parseData(result)
                    
                    withContext(Dispatchers.Main) {
                        resultTextView.text = parsedData
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        resultTextView.text = "Ошибка: неверный хэндл или сбой API"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultTextView.text = "Ошибка сети: ${e.message}"
                }
            }
        }
    }

    private fun parseData(result: org.json.JSONArray): String {
        val solvedProblems = mutableSetOf<String>()
        val problemsByTag = mutableMapOf<String, Int>()
        val problemsByRating = mutableMapOf<Int, Int>()
        
        // Для подсчета попыток (рейтинг -> количество попыток на решенные задачи)
        val attemptsPerSolvedByRating = mutableMapOf<Int, MutableList<Int>>()
        val problemAttempts = mutableMapOf<String, Int>()

        for (i in 0 until result.length()) {
            val submission = result.getJSONObject(i)
            val problem = submission.getJSONObject("problem")
            val verdict = submission.optString("verdict", "")
            
            val problemId = "${problem.optInt("contestId", 0)}${problem.optString("index", "")}"
            val rating = problem.optInt("rating", 0)
            
            // Считаем попытки
            problemAttempts[problemId] = problemAttempts.getOrDefault(problemId, 0) + 1

            if (verdict == "OK" && !solvedProblems.contains(problemId)) {
                solvedProblems.add(problemId)
                
                if (rating > 0) {
                    problemsByRating[rating] = problemsByRating.getOrDefault(rating, 0) + 1
                    
                    if (!attemptsPerSolvedByRating.containsKey(rating)) {
                        attemptsPerSolvedByRating[rating] = mutableListOf()
                    }
                    attemptsPerSolvedByRating[rating]!!.add(problemAttempts[problemId] ?: 1)
                }

                val tags = problem.optJSONArray("tags")
                if (tags != null) {
                    for (j in 0 until tags.length()) {
                        val tag = tags.getString(j)
                        problemsByTag[tag] = problemsByTag.getOrDefault(tag, 0) + 1
                    }
                }
            }
        }

        val sb = java.lang.StringBuilder()
        sb.append("=== ВСЕГО РЕШЕНО: ${solvedProblems.size} ===\n\n")

        sb.append("=== ПО ТЕМАМ (Топ 10) ===\n")
        problemsByTag.entries.sortedByDescending { it.value }.take(10).forEach {
            sb.append("${it.key}: ${it.value}\n")
        }

        sb.append("\n=== ПО РЕЙТИНГУ ===\n")
        problemsByRating.toSortedMap().forEach { (rating, count) ->
            sb.append("Рейтинг $rating: $count задач\n")
        }

        sb.append("\n=== СРЕДНЕЕ ЧИСЛО ПОПЫТОК ПО РЕЙТИНГУ ===\n")
        attemptsPerSolvedByRating.toSortedMap().forEach { (rating, attemptsList) ->
            val avg = attemptsList.average()
            sb.append("Рейтинг $rating: ${String.format("%.1f", avg)} попыток\n")
        }

        return sb.toString()
    }
}
