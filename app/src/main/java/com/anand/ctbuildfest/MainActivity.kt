package com.anand.ctbuildfest

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.anand.ctbuildfest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mapFragment: MapFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.host) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.mapFragment) {
                binding.root.post {
                    mapFragment = supportFragmentManager.fragments
                        .flatMap { it.childFragmentManager.fragments }
                        .firstOrNull { it is MapFragment } as? MapFragment
                }
            }
        }
    }

    fun addDisasterMarker(report: ReportWithLocation) {
        mapFragment?.addUserReportedMarker(report) ?: run {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.host) as? NavHostFragment
            mapFragment = navHostFragment?.childFragmentManager?.fragments
                ?.firstOrNull { it is MapFragment } as? MapFragment
            mapFragment?.addUserReportedMarker(report)
        }
    }
}
