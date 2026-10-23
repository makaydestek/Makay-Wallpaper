package com.makay.wallpaper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MakayWallpaperApp : AppCompatActivity() {

    private const val REQUEST_STORAGE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        // Runtime permission check for READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE)
        } else {
            // Izin zaten verilmiş - başlat
            Toast.makeText(this, "Depolama izni verildi", Toast.LENGTH_SHORT).show()
        }

        val navController: androidx.navigation.NavController = androidx.navigation.fragment.NavHostFragment.findNavController(R.id.fragment)
        setupActionBarWithNavController(navController, drawerLayout = findViewById(R.id.drawer_layout))

        Toast.makeText(this, "Makay Wallpaper Hazır", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Depolama izni verildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "İzin reddedildi, galeriden resim seçilemez", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavUp(): Boolean {
        return false
    }
}