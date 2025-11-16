package de.hsas.inf.cloudapp

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import de.hsas.inf.cloudapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var login: MaterialButton
    private lateinit var saveButton: Button
    private lateinit var filledTextField: TextInputLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var userInfo: TextView
    private lateinit var todoAdapter: TodoAdapter
    private val todoList = mutableListOf<Pair<String, String>>()
    private lateinit var todosRef: DatabaseReference
    private var valueEventListener: ValueEventListener? = null

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        this.onSignInResult(res)
    }

    private val providers = arrayListOf(
        AuthUI.IdpConfig.EmailBuilder().build(),
        AuthUI.IdpConfig.GoogleBuilder().build(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }

        login = findViewById(R.id.login)
        saveButton = findViewById(R.id.save)
        filledTextField = findViewById(R.id.filledTextField)
        recyclerView = findViewById(R.id.recyclerview)
        userInfo = findViewById(R.id.user_info)

        todoAdapter = TodoAdapter(todoList)
        recyclerView.adapter = todoAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        updateUI(FirebaseAuth.getInstance().currentUser)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Erfolgreich angemeldet.", Toast.LENGTH_SHORT).show()
            updateUI(FirebaseAuth.getInstance().currentUser)
        } else {
            Toast.makeText(this, "Anmeldung fehlgeschlagen.", Toast.LENGTH_SHORT).show()
            updateUI(null)
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            userInfo.text = "Willkommen ${user.displayName ?: user.email}"
            login.text = "Abmelden"
            login.setOnClickListener {
                AuthUI.getInstance()
                    .signOut(this)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Erfolgreich abgemeldet.", Toast.LENGTH_SHORT).show()
                            updateUI(null)
                        } else {
                            Toast.makeText(this, "Abmeldung fehlgeschlagen.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }

            saveButton.isEnabled = true
            filledTextField.isEnabled = true

            val database = Firebase.database("https://mobile-cloud-app-2e98d-default-rtdb.europe-west1.firebasedatabase.app/")
            todosRef = database.getReference("todos").child(user.uid)

            saveButton.setOnClickListener {
                val todoText = filledTextField.editText?.text.toString()
                if (todoText.isNotEmpty()) {
                    todosRef.push().setValue(todoText)
                    filledTextField.editText?.text?.clear()
                }
            }

            attachDatabaseReadListener()
            attachSwipeToDelete()

        } else {
            userInfo.text = ""
            login.text = "Anmelden"
            login.setOnClickListener {
                val signInIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .build()
                signInLauncher.launch(signInIntent)
            }

            saveButton.isEnabled = false
            filledTextField.isEnabled = false
            todoList.clear()
            todoAdapter.notifyDataSetChanged()
            detachDatabaseReadListener()
        }
    }

    private fun attachSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val todoId = todoAdapter.getTodoId(position)

                todosRef.child(todoId).removeValue()
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    private fun attachDatabaseReadListener() {
        if (valueEventListener == null) {
            valueEventListener = object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    todoList.clear()
                    for (snapshot in dataSnapshot.children) {
                        val todo = snapshot.getValue(String::class.java)
                        val todoId = snapshot.key
                        if (todo != null && todoId != null) {
                            todoList.add(Pair(todoId, todo))
                        }
                    }
                    todoAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.w("MainActivity", "Failed to read value.", databaseError.toException())
                }
            }
            todosRef.addValueEventListener(valueEventListener!!)
        }
    }

    private fun detachDatabaseReadListener() {
        if (valueEventListener != null) {
            if (this::todosRef.isInitialized) {
                todosRef.removeEventListener(valueEventListener!!)
            }
            valueEventListener = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}