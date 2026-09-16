package com.miguel.cardtarefas

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

// Tela invisivel: aberta pelo botao de microfone do widget. Dispara a gravacao,
// interpreta a frase e mostra a confirmacao — sem abrir o app inteiro.
class VozActivity : AppCompatActivity() {

    private lateinit var store: Store

    private val vozLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val fala = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!fala.isNullOrBlank()) prepararConfirmacao(fala) else finish()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        if (!store.logado) {
            aviso("Entre no app primeiro.")
            finish()
            return
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ex.: adicionar leite valor 10 na lista Mercado")
        try {
            vozLauncher.launch(i)
        } catch (e: Exception) {
            aviso("Reconhecimento de voz indisponivel.")
            finish()
        }
    }

    // busca os grupos-lista (rede) e entao mostra a confirmacao
    private fun prepararConfirmacao(fala: String) {
        Thread {
            try {
                val uid = store.uid!!
                val idToken = Api.idTokenValido(store)
                val grupos = Api.listarGruposLista(idToken, uid)
                runOnUiThread {
                    if (grupos.isEmpty()) {
                        aviso("Nenhuma lista encontrada.")
                        finish()
                    } else {
                        confirmar(interpretarComando(fala, grupos), grupos)
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread { aviso("Erro ao conectar."); finish() }
            }
        }.start()
    }

    private fun confirmar(cmd: ComandoVoz, grupos: List<String>) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(4))

        fun rotulo(txt: String): TextView {
            val t = TextView(this)
            t.text = txt; t.setTextColor(0xFF9A9AA2.toInt()); t.textSize = 12f
            t.setPadding(0, dp(8), 0, dp(2))
            return t
        }
        fun campo(): EditText {
            val e = EditText(this)
            e.setTextColor(0xFFECECF0.toInt())
            e.setBackgroundResource(R.drawable.field_bg)
            e.setPadding(dp(10), dp(8), dp(10), dp(8))
            e.maxLines = 1
            return e
        }

        box.addView(rotulo("Item"))
        val edItem = campo(); edItem.setText(cmd.item); box.addView(edItem)

        box.addView(rotulo("Valor (opcional)"))
        val edValor = campo()
        edValor.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        if (cmd.valor != null) edValor.setText(fmtValor(cmd.valor))
        box.addView(edValor)

        box.addView(rotulo("Lista"))
        val sp = Spinner(this)
        sp.adapter = adaptador(grupos)
        val gi = grupos.indexOfFirst { it.equals(cmd.grupo, true) }
        if (gi >= 0) sp.setSelection(gi)
        sp.setBackgroundResource(R.drawable.field_bg)
        box.addView(sp)

        AlertDialog.Builder(this)
            .setTitle("Confirmar item")
            .setView(box)
            .setPositiveButton("Adicionar") { _, _ ->
                val desc = edItem.text.toString().trim()
                if (desc.isEmpty()) { finish(); return@setPositiveButton }
                val grupo = (sp.selectedItem as? String) ?: grupos.first()
                val valor = parseValor(edValor.text.toString())
                Thread {
                    try {
                        val uid = store.uid!!
                        val idToken = Api.idTokenValido(store)
                        Api.criarItem(idToken, uid, grupo, desc, valor)
                    } catch (_: Throwable) {
                    } finally {
                        runOnUiThread {
                            ListWidget.atualizar(this)
                            aviso("Adicionado em $grupo")
                            finish()
                        }
                    }
                }.start()
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun adaptador(itens: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, itens) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                v.setBackgroundColor(0xFF2C2C2E.toInt())
                v.setPadding(24, 20, 24, 20)
                return v
            }
        }
    }

    private fun aviso(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
