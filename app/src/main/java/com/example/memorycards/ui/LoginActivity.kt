package com.example.memorycards.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.memorycards.R
import com.example.memorycards.data.Api
import com.example.memorycards.data.Session
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private var registerMode = false

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirm: EditText
    private lateinit var btnSubmit: Button
    private lateinit var tvSwitchMode: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etConfirm = findViewById(R.id.etConfirm)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvSwitchMode = findViewById(R.id.tvSwitchMode)

        registerMode = intent.getBooleanExtra(EXTRA_REGISTER, false)
        applyMode()

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        btnSubmit.setOnClickListener { submit() }
        tvSwitchMode.setOnClickListener {
            registerMode = !registerMode
            applyMode()
        }
    }

    private fun applyMode() {
        etConfirm.visibility = if (registerMode) android.view.View.VISIBLE else android.view.View.GONE
        btnSubmit.text = if (registerMode) "注册" else "登录"
        tvSwitchMode.text = if (registerMode) "已有账号？去登录" else "没有账号？去注册"
    }

    private fun submit() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }
        if (registerMode && password != etConfirm.text.toString()) {
            Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
            return
        }
        btnSubmit.isEnabled = false
        val body = JSONObject().put("username", username).put("password", password)
        val path = if (registerMode) "/api/auth/register" else "/api/auth/login"
        Api.request(path, "POST", body.toString(), object : Api.Callback {
            override fun onSuccess(body: String) {
                btnSubmit.isEnabled = true
                runCatching {
                    val json = JSONObject(body)
                    val token = json.optString("token")
                    val user = json.optJSONObject("user")
                    if (token.isNotBlank() && user != null) {
                        Session.save(this@LoginActivity, token, user.optString("username"))
                        Toast.makeText(this@LoginActivity, if (registerMode) "注册成功" else "登录成功", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                        return
                    }
                    Toast.makeText(this@LoginActivity, "响应异常", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this@LoginActivity, "响应异常", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(code: Int, message: String) {
                btnSubmit.isEnabled = true
                Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    companion object {
        const val EXTRA_REGISTER = "register_mode"
    }
}
