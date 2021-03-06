package jp.juggler.subwaytooter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.util.LogCategory

internal class TabletColumnViewHolder(
	activity : ActMain,
	parent: ViewGroup,
	val columnViewHolder : ColumnViewHolder = ColumnViewHolder(activity,parent)
) : RecyclerView.ViewHolder(columnViewHolder.viewRoot) {
	
	companion object {
		val log = LogCategory("TabletColumnViewHolder")
	}
	
	private var pageIndex = - 1
	
	fun bind(column : Column, pageIndex : Int, column_count : Int) {
		log.d("bind. %d => %d ", this.pageIndex, pageIndex)
		
		columnViewHolder.onPageDestroy(this.pageIndex)
		
		this.pageIndex = pageIndex
		
		columnViewHolder.onPageCreate(column, pageIndex, column_count)
		
		if(! column.bFirstInitialized) {
			column.startLoading()
		}
	}
	
	fun onViewRecycled() {
		log.d("onViewRecycled %d", pageIndex)
		columnViewHolder.onPageDestroy(pageIndex)
	}
}
