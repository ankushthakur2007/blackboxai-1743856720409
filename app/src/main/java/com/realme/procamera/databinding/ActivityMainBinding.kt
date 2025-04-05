package com.realme.procamera.databinding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.realme.procamera.R
import com.realme.procamera.GLSurfaceView

class ActivityMainBinding private constructor(
    private val rootView: View
) : ViewBinding {
    val root: View = rootView
    val preview: GLSurfaceView = rootView.findViewById(R.id.preview)
    val fabRecord: FloatingActionButton = rootView.findViewById(R.id.fab_record)

    companion object {
        fun inflate(
            inflater: LayoutInflater,
            parent: ViewGroup? = null,
            attachToParent: Boolean = false
        ): ActivityMainBinding {
            val root = inflater.inflate(
                R.layout.activity_main,
                parent,
                attachToParent
            )
            return ActivityMainBinding(root)
        }
    }

    override fun getRoot(): View = root
}