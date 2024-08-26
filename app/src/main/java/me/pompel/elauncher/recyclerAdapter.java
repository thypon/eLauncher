package me.pompel.elauncher;
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
import java.util.Stack;
import java.util.stream.Collectors;

public class recyclerAdapter extends RecyclerView.Adapter<recyclerAdapter.AppViewHolder> implements Filterable {
    private final ArrayList<AndroidClickable> clickableList;
    private ArrayList<AndroidClickable> clickableFiltered;
    private final RecyclerViewClickListener listener;

    public recyclerAdapter(ArrayList<AndroidClickable> clickableList, RecyclerViewClickListener listener) {
        this.clickableList = clickableList;
        this.clickableFiltered = clickableList;
        this.listener = listener;
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
        return new LinkedList<>(str.chars().mapToObj(e -> (char)e).collect(Collectors.toList()));
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String str = charSequence.toString().toLowerCase();
                FilterResults results = new FilterResults();
                List<AndroidClickable> filteredClickables = new ArrayList<>();

                if (str.isEmpty()) filteredClickables = clickableList;
                else for (AndroidClickable clickable : clickableList) if (fuzzyContains(clickable.label().toString().toLowerCase(), str)) filteredClickables.add(clickable);

                results.count = filteredClickables.size();
                results.values = filteredClickables;
                return results;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                clickableFiltered = (ArrayList<AndroidClickable>) filterResults.values;

                for (AndroidClickable clickable : clickableFiltered) {
                    // extract the clickable name characters which were matched by the filter
                    // the match is case-insensitive and it's a fuzzy match
                    Queue<Character> clickableNameQueue = toCharacterList(clickable.label().toString().toLowerCase());
                    Queue<Character> matchQueue = toCharacterList(charSequence.toString().toLowerCase());

                    if (matchQueue.isEmpty() || clickableNameQueue.isEmpty()) {
                        // GUARD: if either of the two queue are empty after construction
                        Log.d("IMPOSSIBLE_STATE", "any of matchQueue and clickableNameQueue are empty after construction!");
                        break;
                    }
                    Stack<Integer> matchedIndexes = new Stack<>();

                    int i = 0;
                    
                    while (!clickableNameQueue.isEmpty() && !matchQueue.isEmpty()) {
                        if (clickableNameQueue.peek() == matchQueue.peek()) {
                            matchedIndexes.push(i);
                            matchQueue.poll();
                        }

                        clickableNameQueue.poll();
                        i++;
                    }

                    // if an exact match, exit and click on it
                    if (clickableNameQueue.isEmpty() && matchQueue.isEmpty()) {
                        listener.onClick(clickable);
                        break;
                    }

                    // apply the span to the matched characters
                    for (int index : matchedIndexes) {
                        clickable.label().setSpan(new StyleSpan(Typeface.BOLD), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        clickable.label().setSpan(new UnderlineSpan(), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }

                if (clickableFiltered.size() == 1) listener.onClick(clickableFiltered.get(0));
                notifyDataSetChanged();
            }
        };
    }

    public class AppViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView nameText;

        @Override
        public void onClick(View view) { listener.onClick(clickableFiltered.get(getAdapterPosition())); }

        @Override
        public boolean onLongClick(View view) {
            listener.onLongClick(clickableFiltered.get(getAdapterPosition()));
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
        SpannableString clickableName = clickableFiltered.get(position).label();
        holder.nameText.setText(clickableName);

        // remove all the spans after the string has been set
        Object[] spans = clickableName.getSpans(0, clickableName.length(), Object.class);
        for (Object span : spans) {
            clickableName.removeSpan(span);
        }
    }

    @Override
    public int getItemCount() {
        return clickableFiltered.size();
    }

    public interface RecyclerViewClickListener {
        void onClick(AndroidClickable clickable);
        void onLongClick(AndroidClickable clickable);
    }
}
