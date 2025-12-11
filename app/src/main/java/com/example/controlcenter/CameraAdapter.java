package com.example.controlcenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CameraAdapter extends RecyclerView.Adapter<CameraAdapter.CameraViewHolder> {

    private final List<String> cameraDescriptions;
    private final OnCameraClickListener listener;

    public interface OnCameraClickListener {
        void onCameraClick(int position);
    }

    public CameraAdapter(List<String> cameraDescriptions, OnCameraClickListener listener) {
        this.cameraDescriptions = cameraDescriptions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CameraViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_camera_option, parent, false);
        return new CameraViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CameraViewHolder holder, int position) {
        holder.cameraName.setText(cameraDescriptions.get(position));
        holder.itemView.setOnClickListener(v -> listener.onCameraClick(position));
    }

    @Override
    public int getItemCount() {
        return cameraDescriptions.size();
    }

    static class CameraViewHolder extends RecyclerView.ViewHolder {
        TextView cameraName;

        CameraViewHolder(View itemView) {
            super(itemView);
            cameraName = itemView.findViewById(R.id.camera_name);
        }
    }
}
