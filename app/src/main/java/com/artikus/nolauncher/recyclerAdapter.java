package com.artikus.nolauncher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class recyclerAdapter extends RecyclerView.Adapter<recyclerAdapter.AppViewHolder> implements Filterable {
    private final ArrayList<App> appList;
    private ArrayList<App> appListFiltered;
    private final RecyclerViewClickListener listener;

    public recyclerAdapter(ArrayList<App> appList, RecyclerViewClickListener listener) {
        this.appList = appList;
        this.appListFiltered = appList;
        this.listener = listener;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String str = charSequence.toString().toLowerCase();
                FilterResults results = new FilterResults();
                List<App> filteredApps = new ArrayList<>();

                if (str.isEmpty()) filteredApps = appList;
                else for (App app : appList) if (app.appName.toLowerCase().contains(str)) filteredApps.add(app);

                results.count = filteredApps.size();
                results.values = filteredApps;
                return results;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                appListFiltered = (ArrayList<App>)filterResults.values;
                if (appListFiltered.size() == 1) listener.onClick(appListFiltered.get(0));
                notifyDataSetChanged();
            }
        };
    }

    public class AppViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView nameText;

        @Override
        public void onClick(View view) { listener.onClick(appListFiltered.get(getAdapterPosition())); }

        @Override
        public boolean onLongClick(View view) {
            listener.onLongClick(appListFiltered.get(getAdapterPosition()));
            return true;
        }

        public AppViewHolder(final View view) {
            super(view);
            nameText = view.findViewById(R.id.app_name);
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }
    }

    @NonNull
    @Override
    public recyclerAdapter.AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull recyclerAdapter.AppViewHolder holder, int position) {
        holder.nameText.setText(appListFiltered.get(position).appName);
    }

    @Override
    public int getItemCount() {
        return appListFiltered.size();
    }

    public interface RecyclerViewClickListener {
        void onClick(App app);
        void onLongClick(App app);
    }
}
