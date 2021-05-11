package jp.juggler.subwaytooter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.util.LogCategory
import jp.juggler.util.asciiPattern
import jp.juggler.util.replaceFirst
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class ActDrawableList : AppCompatActivity(), CoroutineScope {
	
	companion object {
		private val log = LogCategory("ActDrawableList")
	}
	
	private lateinit var activityJob : Job
	
	override val coroutineContext : CoroutineContext
		get() = Dispatchers.Main + activityJob
	
	private class MyItem(val id : Int, val name : String)
	
	private val drawableList = ArrayList<MyItem>()
	private lateinit var adapter : MyAdapter
	private lateinit var listView : ListView
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this)
		activityJob = Job()
		initUI()
		load()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		activityJob.cancel()
	}
	
	private fun initUI() {
		setContentView(R.layout.act_drawable_list)
		App1.initEdgeToEdge(this)
		Styler.fixHorizontalPadding(findViewById(R.id.llContent))
		
		listView = findViewById(R.id.listView)
		adapter = MyAdapter()
		listView.adapter = adapter
	}
	
	private fun load() = launch {
		try {
			val rePackageSpec = """.+/""".asciiPattern()
			val reSkipName = """^(abc_|avd_|btn_checkbox_|btn_radio_|googleg_|ic_keyboard_arrow_|ic_menu_arrow_|notification_|common_|emj_|cpv_|design_|exo_|mtrl_|ic_mtrl_)"""
				.asciiPattern()
			val list = withContext(Dispatchers.IO) {
				R.drawable::class.java.fields
					.mapNotNull {
						val id = it.get(null) as? Int ?: return@mapNotNull null
						val name = resources.getResourceName(id).replaceFirst(rePackageSpec, "")
						if(reSkipName.matcher(name).find() ) return@mapNotNull null
						MyItem(id, name)
					}
					.toMutableList()
					.apply { sortBy { it.name } }
				
			}
			drawableList.clear()
			drawableList.addAll(list)
			adapter.notifyDataSetChanged()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
	private class MyViewHolder(viewRoot : View) {
		
		private val tvCaption : TextView = viewRoot.findViewById(R.id.tvCaption)
		private val ivImage : ImageView = viewRoot.findViewById(R.id.ivImage)
		
		fun bind(item : MyItem) {
			tvCaption.text = item.name
			ivImage.setImageResource(item.id)
		}
	}
	
	private inner class MyAdapter : BaseAdapter() {
		
		override fun getCount() : Int = drawableList.size
		override fun getItemId(idx : Int) : Long = 0L
		override fun getItem(idx : Int) : Any = drawableList[idx]
		
		override fun getView(idx : Int, viewArg : View?, parent : ViewGroup?) : View {
			val view : View
			val holder : MyViewHolder
			if(viewArg == null) {
				view = layoutInflater.inflate(R.layout.lv_drawable, parent, false)
				holder = MyViewHolder(view)
				view.tag = holder
			} else {
				view = viewArg
				holder = view.tag as MyViewHolder
			}
			holder.bind(drawableList[idx])
			return view
		}
		
	}
	
}
