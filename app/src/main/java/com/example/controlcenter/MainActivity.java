package com.example.controlcenter;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private CommunicationViewModel viewModel;
    private TextView statusTextView;
    private TextView connectionStatusTextView;
    private TextView peerStatusTextView;
    private PhotoView imageView;
    private ScrollView scrollView;
    private TextView imageSizeTextView;
    private ProgressBar progressBarImage;
    private ProgressBar progressBarConnect;

    // Новый контейнер для кнопок
    private LinearLayout cameraButtonsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация UI элементов
        statusTextView = findViewById(R.id.statusTextView);
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);
        peerStatusTextView = findViewById(R.id.peerStatusTextView);
        scrollView = findViewById(R.id.scrollView);
        imageView = findViewById(R.id.imageView);
        imageSizeTextView = findViewById(R.id.imageSizeTextView);
        progressBarImage = findViewById(R.id.progressBarImage);
        progressBarConnect = findViewById(R.id.progressBarConnect);
        cameraButtonsContainer = findViewById(R.id.cameraButtonsContainer);

        progressBarImage.setMax(100);

        // --- MVVM: Инициализация ViewModel ---
        viewModel = new ViewModelProvider(this).get(CommunicationViewModel.class);
        viewModel.initWakeLock(getApplicationContext());
        observeViewModel();

        if (viewModel.getConnectionStatus().getValue() == null) {
            viewModel.startConnection("5.35.102.58", 8080);
            Log.d(TAG, "Первый запуск: инициируем соединение");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.decodePendingPhoto();

        Bitmap currentBitmap = viewModel.getNewImageBitmap().getValue();
        if (currentBitmap != null) {
            imageView.setImageBitmap(currentBitmap);
            imageView.setVisibility(View.VISIBLE);
            showLoading(false);
        }
    }

    private void observeViewModel() {
        // Статус соединения с сервером
        viewModel.getConnectionStatus().observe(this, status -> {
            connectionStatusTextView.setText("Статус: " + status);
            if ("Установлено".equals(status)) {
                int color = ContextCompat.getColor(this, android.R.color.holo_green_dark);
                connectionStatusTextView.setTextColor(color);
                progressBarConnect.setVisibility(View.GONE);
            } else {
                connectionStatusTextView.setTextColor(Color.RED);
                progressBarConnect.setVisibility(View.VISIBLE);
            }
        });

        // Статус удаленного клиента (пира)
        viewModel.getPeerStatus().observe(this, status -> {
            peerStatusTextView.setText("Удаленный клиент: " + status);
            if ("Подключен".equals(status)) {
                peerStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
            } else {
                peerStatusTextView.setTextColor(Color.parseColor("#FFA500"));
            }
        });

        // Логика динамического создания кнопок камер
        viewModel.getCameraDescriptions().observe(this, descriptions -> {
            updateCameraButtons(descriptions, viewModel.getCameraIdsList().getValue());
        });

        // Картинка
        viewModel.getNewImageBitmap().observe(this, bitmap -> {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);

                // Плавное появление (Fade In)
                imageView.setAlpha(0f); // Сначала прозрачный
                imageView.setVisibility(View.VISIBLE);
                imageView.animate()
                        .alpha(1f) // Становится непрозрачным
                        .setDuration(500) // Полдюжины секунд
                        .setListener(null);

                imageSizeTextView.setVisibility(View.VISIBLE);
                showLoading(false);
            } else {
                // Плавное исчезновение (Fade Out)
                imageView.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            imageView.setVisibility(View.GONE);
                            imageView.setImageDrawable(null);
                        });

                imageSizeTextView.setVisibility(View.GONE);
            }
        });



        viewModel.getImageLoadProgress().observe(this, progress -> {
            progressBarImage.setProgress(progress);
        });

        viewModel.getImageSizeText().observe(this, sizeText -> {
            if (sizeText == null || sizeText.isEmpty()) {
                imageSizeTextView.setVisibility(View.GONE);
            } else {
                imageSizeTextView.setText(sizeText);
                // Показываем только если мы НЕ в режиме ожидания нового запроса
                // (или убедитесь, что в resetImage() вы шлете сюда пустую строку)
                imageSizeTextView.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getIsLoading().observe(this, this::showLoading);

        viewModel.getIsProgressIndeterminate().observe(this, isInd -> {
            progressBarImage.setIndeterminate(isInd);
        });

        viewModel.getStatusMessages().observe(this, message -> {
            statusTextView.append("\nСервер: " + message);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    /**
     * Создает кнопки для каждой найденной камеры
     */
    private void updateCameraButtons(List<String> descriptions, List<Integer> ids) {
        if (descriptions == null || ids == null || descriptions.size() != ids.size()) return;

        cameraButtonsContainer.removeAllViews(); // Очищаем старые кнопки

        // 1. Подготавливаем цвета для состояний (Активна / Заблокирована)
        int colorActive = ContextCompat.getColor(this, R.color.bottom_sheet_background);
        int colorDisabled = Color.parseColor("#888888"); // Серый цвет

        // Создаем список состояний: какой цвет когда рисовать
        android.content.res.ColorStateList buttonStates = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled}, // Состояние: Выключена
                        new int[]{android.R.attr.state_enabled}   // Состояние: Включена
                },
                new int[]{
                        colorDisabled,
                        colorActive
                }
        );

        // 2. Базовая иконка
        android.graphics.drawable.Drawable cameraIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_camera);

        for (int i = 0; i < descriptions.size(); i++) {
            String desc = descriptions.get(i);
            int id = ids.get(i);

            // Используем MaterialButton для правильной работы с иконками и цветами
            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this);

            btn.setText(desc);
            btn.setTextColor(Color.WHITE);
            btn.setAllCaps(false);
            btn.setTextSize(13);

            // Настройки размеров и отступов для MaterialButton
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setPadding(0, 0, 0, 0);
            btn.setMaxLines(1);
            btn.setEllipsize(android.text.TextUtils.TruncateAt.END);

            // Устанавливаем наш селектор цветов на фон
            btn.setBackgroundTintList(buttonStates);

            // Настройка иконки
            if (cameraIcon != null) {
                android.graphics.drawable.Drawable iconCopy = cameraIcon.getConstantState().newDrawable().mutate();
                iconCopy.setTint(Color.WHITE);
                int iconSize = (int) (18 * getResources().getDisplayMetrics().density);

                btn.setIcon(iconCopy);
                btn.setIconSize(iconSize);
                btn.setIconPadding(8);
                // Центрируем иконку вместе с текстом
                btn.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
            }

            // Настройка веса (чтобы кнопки были строго одинаковой ширины)
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            params.setMargins(4, 0, 4, 0);
            btn.setLayoutParams(params);

            // Логика нажатия
            btn.setOnClickListener(v -> {
                viewModel.lockInterfaceBeforeRequest();
                viewModel.sendCommand("TAKE_PHOTO_" + id);
            });

            // Подписка на состояние доступности
            viewModel.getIsButtonEnabled().observe(this, isEnabled -> {
                btn.setEnabled(isEnabled);
                // Дополнительно меняем прозрачность, чтобы кнопка выглядела "приглушенной"
                btn.setAlpha(isEnabled ? 1.0f : 0.7f);
            });

            cameraButtonsContainer.addView(btn);
        }
    }




    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBarImage.setVisibility(View.VISIBLE);
        } else {
            progressBarImage.setVisibility(View.GONE);
        }
    }
}
