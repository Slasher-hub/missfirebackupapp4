package com.example.missfirebackupapp

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import android.widget.ImageView
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class PhotoGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_gallery)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarGallery)
        toolbar.setNavigationOnClickListener { finish() }

        val uris = intent.getStringArrayListExtra("photo_uris")?.map { Uri.parse(it) } ?: emptyList()
        val pager = findViewById<ViewPager2>(R.id.viewPagerFotos)
        pager.adapter = PhotoPagerAdapter(uris)
    }

    private inner class PhotoPagerAdapter(private val fotos: List<Uri>) : RecyclerView.Adapter<PhotoVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH =
            PhotoVH(ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            })
        override fun getItemCount(): Int = fotos.size
        override fun onBindViewHolder(holder: PhotoVH, position: Int) {
            holder.bind(fotos[position])
        }
    }
    private inner class PhotoVH(private val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        fun bind(uri: Uri) { imageView.setImageURI(uri) }
    }
}
