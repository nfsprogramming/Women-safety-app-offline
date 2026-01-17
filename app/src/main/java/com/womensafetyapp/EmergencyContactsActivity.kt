package com.womensafetyapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.ViewGroup
import org.json.JSONArray

class EmergencyContactsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmergencyContactsAdapter
    private lateinit var fabAddContact: FloatingActionButton
    private lateinit var tvEmptyState: TextView
    private lateinit var sharedPreferences: android.content.SharedPreferences

    companion object {
        private const val SHARED_PREFS_NAME = "WomenSafetyPrefs"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_contacts)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Emergency Contacts"

        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        
        initViews()
        setupRecyclerView()
        loadContacts()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        fabAddContact = findViewById(R.id.fabAddContact)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        
        fabAddContact.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = EmergencyContactsAdapter(
            mutableListOf(),
            onDeleteClick = { contact ->
                deleteContact(contact)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadContacts() {
        val contacts = getEmergencyContacts()
        adapter.updateContacts(contacts)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            tvEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)

        AlertDialog.Builder(this)
            .setTitle("Add Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    addContact(name, phone)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addContact(name: String, phone: String) {
        val contacts = getEmergencyContacts().toMutableList()
        contacts.add(EmergencyContact(name, phone))
        saveContacts(contacts)
        
        adapter.updateContacts(contacts)
        updateEmptyState()
        Toast.makeText(this, "Contact added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun deleteContact(contact: EmergencyContact) {
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                val contacts = getEmergencyContacts().toMutableList()
                contacts.remove(contact)
                saveContacts(contacts)
                
                adapter.updateContacts(contacts)
                updateEmptyState()
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getEmergencyContacts(): List<EmergencyContact> {
        val contactsJson = sharedPreferences.getString(KEY_EMERGENCY_CONTACTS, "[]")
        return try {
            val contacts = mutableListOf<EmergencyContact>()
            val jsonArray = JSONArray(contactsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                contacts.add(EmergencyContact(
                    name = jsonObj.getString("name"),
                    phone = jsonObj.getString("phone")
                ))
            }
            contacts
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveContacts(contacts: List<EmergencyContact>) {
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            val jsonObj = org.json.JSONObject().apply {
                put("name", contact.name)
                put("phone", contact.phone)
            }
            jsonArray.put(jsonObj)
        }
        
        sharedPreferences.edit()
            .putString(KEY_EMERGENCY_CONTACTS, jsonArray.toString())
            .apply()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class EmergencyContactsAdapter(
    private var contacts: MutableList<EmergencyContact>,
    private val onDeleteClick: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.tvName.text = contact.name
        holder.tvPhone.text = contact.phone
        
        holder.btnDelete.setOnClickListener {
            onDeleteClick(contact)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<EmergencyContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }
}