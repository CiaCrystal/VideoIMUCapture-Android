package se.lth.math.videoimucapture;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class CaptureSettings extends PreferenceFragmentCompat {
    public static final String TAG = "VIMUC-CaptureSettings";
    private static final String GRID_ENABLED_KEY = "enable_grid_experiment";
    private static final String GRID_MODE_KEY = "grid_experiment_mode";
    private static final String GRID_CUSTOM_TARGETS_KEY = "grid_experiment_custom_targets";
    private static final String GRID_MODE_RANDOM = "random";
    private static final String GRID_MODE_CUSTOM = "custom";

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "View created -- setting up actionbar");
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = (Toolbar) getView().findViewById(R.id.topAppBar);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // back button pressed
                getActivity().getSupportFragmentManager().popBackStackImmediate();
            }
        });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        /* This will somehow override default values we set when starting the activity.
           Therefore we set them as not persistent in the XML file.
         */
        setPreferencesFromResource(R.xml.settings, rootKey);

        CameraSettingsManager cameraSettingsManager = ((CameraCaptureActivity) getActivity()).getmCameraSettingsManager();
        cameraSettingsManager.updatePreferences(getPreferenceScreen());
        configureGridExperimentPreferences();

    }

    @Override
    public void onDestroyView() {
        CameraCaptureActivity activity = (CameraCaptureActivity) getActivity();
        if (activity != null && !BackgroundCaptureService.isActive()) {
            activity.getmImuManager().refreshSamplingRates();
        }
        super.onDestroyView();
    }

    private void configureGridExperimentPreferences() {
        SwitchPreferenceCompat enabledPreference = findPreference(GRID_ENABLED_KEY);
        ListPreference modePreference = findPreference(GRID_MODE_KEY);
        MultiSelectListPreference targetsPreference = findPreference(GRID_CUSTOM_TARGETS_KEY);
        if (enabledPreference == null || modePreference == null || targetsPreference == null) return;

        if (modePreference.getValue() == null) modePreference.setValue(GRID_MODE_RANDOM);
        Set<String> selectedTargets = targetsPreference.getValues();
        if (selectedTargets == null || selectedTargets.isEmpty()) {
            selectedTargets = allGridTargets();
            targetsPreference.setValues(selectedTargets);
        }
        updateCustomTargetsSummary(targetsPreference, selectedTargets);
        updateGridPreferenceVisibility(enabledPreference.isChecked(), modePreference.getValue(),
                modePreference, targetsPreference);

        enabledPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            updateGridPreferenceVisibility((Boolean) newValue, modePreference.getValue(),
                    modePreference, targetsPreference);
            return true;
        });
        modePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            updateGridPreferenceVisibility(enabledPreference.isChecked(), (String) newValue,
                    modePreference, targetsPreference);
            return true;
        });
        targetsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            @SuppressWarnings("unchecked")
            Set<String> newTargets = (Set<String>) newValue;
            if (newTargets.isEmpty()) {
                Toast.makeText(requireContext(), "自定义模式至少选择一个数字。", Toast.LENGTH_SHORT).show();
                return false;
            }
            updateCustomTargetsSummary(targetsPreference, newTargets);
            return true;
        });
    }

    private void updateGridPreferenceVisibility(boolean enabled, String mode,
                                                ListPreference modePreference,
                                                MultiSelectListPreference targetsPreference) {
        modePreference.setEnabled(enabled);
        boolean custom = GRID_MODE_CUSTOM.equals(mode);
        targetsPreference.setVisible(custom);
        targetsPreference.setEnabled(enabled && custom);
    }

    private void updateCustomTargetsSummary(MultiSelectListPreference preference,
                                            Set<String> selectedTargets) {
        List<Integer> numbers = new ArrayList<>();
        for (String value : selectedTargets) {
            try {
                int number = Integer.parseInt(value);
                if (number >= 1 && number <= 9) numbers.add(number);
            } catch (NumberFormatException ignored) {
                // Ignore values left by an incompatible older build.
            }
        }
        Collections.sort(numbers);
        StringBuilder summary = new StringBuilder("Numbers: ");
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) summary.append(", ");
            summary.append(numbers.get(i));
        }
        preference.setSummary(summary.toString());
    }

    private Set<String> allGridTargets() {
        return new HashSet<>(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9"));
    }

}
