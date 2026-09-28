package app.mosaicos.blutilities.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.res.use
import androidx.recyclerview.widget.RecyclerView
import app.mosaicos.blutilities.R

/** RecyclerView that honours android:maxHeight, which the base class silently ignores. */
class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val maxHeightPx = context
        .obtainStyledAttributes(attrs, R.styleable.MaxHeightRecyclerView)
        .use { it.getDimensionPixelSize(R.styleable.MaxHeightRecyclerView_android_maxHeight, 0) }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (maxHeightPx <= 0) return super.onMeasure(widthSpec, heightSpec)

        val available = when (MeasureSpec.getMode(heightSpec)) {
            MeasureSpec.UNSPECIFIED -> maxHeightPx
            else -> minOf(maxHeightPx, MeasureSpec.getSize(heightSpec))
        }
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
    }
}
