package app.mango.patternlockview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import app.mango.patternlockview.views.PatternLockView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val auto = findViewById<PatternLockView>(R.id.patter_lock_view_auto)
        auto.setInputEnabled(false)
        findViewById<PatternLockView>(R.id.patter_lock_view).run {
            setPatternLockListener(
                onComplete = {
                    auto.setPattern(PatternLockView.PatternViewMode.AUTO_DRAW, it)
                }
            )
        }
    }
}