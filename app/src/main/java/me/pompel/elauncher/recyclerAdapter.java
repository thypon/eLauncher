package me.pompel.elauncher;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

public class recyclerAdapter extends RecyclerView.Adapter<recyclerAdapter.AppViewHolder> implements Filterable {
    private final ArrayList<App> appList;
    private ArrayList<App> appListFiltered;
    private final RecyclerViewClickListener listener;
    private Set<String> processPackages;

    public recyclerAdapter(ArrayList<App> appList, Set<String> processPackages, RecyclerViewClickListener listener) {
        this.appList = appList;
        this.appListFiltered = appList;
        this.listener = listener;
        this.processPackages = processPackages;
    }

    public void setProcessPackages(Set<String> processPackages) {
        this.processPackages = processPackages;
    }

    private static boolean fuzzyContains(String str, String query) {
        int strIndex = 0;
        for (char c : query.toCharArray()) {
            strIndex = str.indexOf(c, strIndex);
            if (strIndex == -1) return false;
        }
        return true;
    }

    private static LinkedList<Character> toCharacterList(String str) {
        return str.chars().mapToObj(e -> (char) e).collect(Collectors.toCollection(LinkedList::new));
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
                else for (App app : appList) if (fuzzyContains(app.appName.toString().toLowerCase(), str)) filteredApps.add(app);

                results.count = filteredApps.size();
                results.values = filteredApps;
                return results;
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                appListFiltered = (ArrayList<App>)filterResults.values;

                for (App app : appListFiltered) {
                    // extract the app name characters which were matched by the filter
                    // the match is case-insensitive and it's a fuzzy match
                    Queue<Character> appNameQueue = toCharacterList(app.appName.toString().toLowerCase());
                    Queue<Character> matchQueue = toCharacterList(charSequence.toString().toLowerCase());

                    if (matchQueue.isEmpty() || appNameQueue.isEmpty()) {
                        // GUARD: if either of the two queue are empty after construction
                        Log.d("IMPOSSIBLE_STATE", "any of matchQueue and appNameQueue are empty after construction!");
                        break;
                    }
                    Stack<Integer> matchedIndexes = new Stack<>();

                    int i = 0;
                    
                    while (!appNameQueue.isEmpty() && !matchQueue.isEmpty()) {
                        if (appNameQueue.peek() == matchQueue.peek()) {
                            matchedIndexes.push(i);
                            matchQueue.poll();
                        }

                        appNameQueue.poll();
                        i++;
                    }

                    // if an exact match, exit and click on it
                    if (app.appName.length() == charSequence.length() && appNameQueue.isEmpty() && matchQueue.isEmpty()) {
                        listener.onClick(app);
                        break;
                    }

                    // apply the span to the matched characters
                    for (int index : matchedIndexes) {
                        // app.appName.setSpan(new StyleSpan(Typeface.BOLD), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        app.appName.setSpan(new UnderlineSpan(), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }

                if (appListFiltered.size() == 1) listener.onClick(appListFiltered.get(0));
                notifyDataSetChanged();
            }
        };
    }

    public class AppViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView nameText;

        @Override
        public void onClick(View view) { listener.onClick(appListFiltered.get(getAbsoluteAdapterPosition())); }

        @Override
        public boolean onLongClick(View view) {
            listener.onLongClick(appListFiltered.get(getAbsoluteAdapterPosition()));
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
        SpannableString appName = appListFiltered.get(position).appName;
        String packageId = appListFiltered.get(position).packageId;
        holder.nameText.setText(appName);

        // remove all the spans after the string has been set
        Object[] spans = appName.getSpans(0, appName.length(), Object.class);
        for (Object span : spans) {
            appName.removeSpan(span);
        }

        if (processPackages != null && processPackages.contains(packageId)) {
            appName.setSpan(new StyleSpan(Typeface.BOLD), 0, appName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
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
