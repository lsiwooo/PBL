package com.moa.pbl.analysis;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Optional profile form for future real cohort comparison. */
public final class ProfileFormView extends LinearLayout {

    public interface OnSaved {
        void onSaved(ConsumptionProfile profile);
    }

    private static final int BLUE = 0xff1b4fa3;
    private static final int TEXT = 0xff172642;
    private static final int BORDER = 0xffdfe5dc;

    private final Button birthDateButton;
    private final Spinner occupationSpinner;
    private final TextView schoolLabel;
    private final Spinner schoolSpinner;
    private final TextView schoolNameLabel;
    private final EditText schoolNameField;
    private final TextView gradeLabel;
    private final Spinner gradeSpinner;
    private final Spinner regionSpinner;
    private LocalDate birthDate;
    private int lastSchoolPosition;

    public ProfileFormView(Context context, @Nullable ConsumptionProfile initial, OnSaved onSaved) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(18), dp(16), dp(18), dp(16));
        setBackground(AnalysisViews.rounded(context, 0xffffffff, 24, 0xffe7e8e8));

        label("생년월일");
        birthDateButton = fieldButton("생년월일 선택");
        addView(birthDateButton, new LayoutParams(-1, dp(48)));
        birthDateButton.setOnClickListener(v -> showDatePicker());

        label("직업");
        occupationSpinner = spinner(withPrompt(Arrays.asList(ConsumptionProfile.OCCUPATIONS)));

        schoolLabel = label("학교");
        schoolSpinner = spinner(withPrompt(Arrays.asList(ConsumptionProfile.SCHOOL_TYPES)));
        schoolNameLabel = label("학교 이름");
        schoolNameField = new EditText(context);
        schoolNameField.setSingleLine(true);
        schoolNameField.setTextSize(15);
        schoolNameField.setTextColor(TEXT);
        schoolNameField.setHint("학교 이름을 입력해 주세요");
        schoolNameField.setPadding(dp(14), 0, dp(14), 0);
        schoolNameField.setBackground(AnalysisViews.rounded(context, 0xffffffff, 14, BORDER));
        addView(schoolNameField, new LayoutParams(-1, dp(48)));
        gradeLabel = label("학년");
        gradeSpinner = spinner(withPrompt(gradesFor("")));
        label("시·도");
        regionSpinner = spinner(withPrompt(Arrays.asList(ConsumptionProfile.REGIONS)));

        TextView note = AnalysisViews.text(context,
                "프로필 입력은 선택 사항이에요.", 13, 0xff7e8798, false);
        note.setPadding(0, dp(10), 0, 0);
        addView(note);

        Button save = new Button(context);
        save.setText("저장");
        save.setAllCaps(false);
        save.setTextSize(15);
        save.setTextColor(0xffffffff);
        save.setBackground(AnalysisViews.rounded(context, BLUE, 14, BLUE));
        LayoutParams saveParams = new LayoutParams(-1, dp(52));
        saveParams.setMargins(0, dp(16), 0, 0);
        addView(save, saveParams);
        save.setOnClickListener(v -> submit(onSaved));

        if (initial != null) {
            birthDate = initial.birthDate;
            birthDateButton.setText(birthDate.toString());
            setSelection(occupationSpinner, initial.occupation);
            updateStudentFields();
            if (ConsumptionProfile.STUDENT.equals(initial.occupation)) {
                setSelection(schoolSpinner, initial.schoolType);
                schoolNameField.setText(initial.schoolName);
                updateGrades(false);
                setSelection(gradeSpinner, initial.grade + "학년");
            }
            setSelection(regionSpinner, initial.region);
        } else {
            updateStudentFields();
        }
        lastSchoolPosition = schoolSpinner.getSelectedItemPosition();
        schoolSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position != lastSchoolPosition) {
                    lastSchoolPosition = position;
                    updateGrades(true);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        occupationSpinner.setOnItemSelectedListener(new SimpleSelectionListener(this::updateStudentFields));
    }

    private void submit(OnSaved onSaved) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            Toast.makeText(getContext(), "생년월일을 확인해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        int occupationPosition = occupationSpinner.getSelectedItemPosition();
        int regionPosition = regionSpinner.getSelectedItemPosition();
        if (occupationPosition == 0 || regionPosition == 0) {
            Toast.makeText(getContext(), "직업과 시·도를 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        String occupation = ConsumptionProfile.OCCUPATIONS[occupationPosition - 1];
        String schoolType = "";
        String schoolName = "";
        int grade = 0;
        if (ConsumptionProfile.STUDENT.equals(occupation)) {
            int schoolPosition = schoolSpinner.getSelectedItemPosition();
            int gradePosition = gradeSpinner.getSelectedItemPosition();
            schoolName = schoolNameField.getText().toString().trim();
            if (schoolPosition == 0 || gradePosition == 0 || schoolName.isEmpty()) {
                Toast.makeText(getContext(), "학교와 학교 이름, 학년을 입력해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            schoolType = ConsumptionProfile.SCHOOL_TYPES[schoolPosition - 1];
            grade = gradePosition;
        }
        String region = ConsumptionProfile.REGIONS[regionPosition - 1];
        onSaved.onSaved(new ConsumptionProfile(birthDate, occupation, schoolType, schoolName, grade, region));
    }

    private void showDatePicker() {
        LocalDate initial = birthDate == null ? LocalDate.now().minusYears(20) : birthDate;
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (picker, year, month, day) -> {
            birthDate = LocalDate.of(year, month + 1, day);
            birthDateButton.setText(birthDate.toString());
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void updateStudentFields() {
        boolean student = occupationSpinner.getSelectedItemPosition() > 0
                && ConsumptionProfile.STUDENT.equals(occupationSpinner.getSelectedItem().toString());
        schoolLabel.setVisibility(student ? VISIBLE : GONE);
        schoolSpinner.setVisibility(student ? VISIBLE : GONE);
        schoolNameLabel.setVisibility(student ? VISIBLE : GONE);
        schoolNameField.setVisibility(student ? VISIBLE : GONE);
        gradeLabel.setVisibility(student ? VISIBLE : GONE);
        gradeSpinner.setVisibility(student ? VISIBLE : GONE);
        if (student) updateGrades(false);
    }

    private void updateGrades(boolean clearSelection) {
        if (gradeSpinner == null) return;
        String schoolType = schoolSpinner.getSelectedItemPosition() > 0
                ? schoolSpinner.getSelectedItem().toString() : "";
        List<String> grades = withPrompt(gradesFor(schoolType));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, grades);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gradeSpinner.setAdapter(adapter);
        if (!clearSelection && grades.size() > 1) gradeSpinner.setSelection(1);
    }

    private static List<String> gradesFor(String schoolType) {
        int count;
        if ("초등학교".equals(schoolType)) count = 6;
        else if ("중학교".equals(schoolType) || "고등학교".equals(schoolType)) count = 3;
        else if ("대학교".equals(schoolType)) count = 6;
        else count = 0;
        List<String> grades = new ArrayList<>();
        for (int grade = 1; grade <= count; grade++) grades.add(grade + "학년");
        return grades;
    }

    private TextView label(String value) {
        TextView view = AnalysisViews.text(getContext(), value, 13, 0xff7e8798, true);
        view.setPadding(0, dp(10), 0, dp(4));
        addView(view);
        return view;
    }

    private Button fieldButton(String value) {
        Button button = new Button(getContext());
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(AnalysisViews.rounded(getContext(), 0xffffffff, 14, BORDER));
        return button;
    }

    private Spinner spinner(List<String> items) {
        Spinner spinner = new Spinner(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(AnalysisViews.rounded(getContext(), 0xffffffff, 14, BORDER));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        addView(spinner, new LayoutParams(-1, dp(48)));
        return spinner;
    }

    private static List<String> withPrompt(List<String> values) {
        List<String> result = new ArrayList<>();
        result.add("선택하세요");
        result.addAll(values);
        return result;
    }

    private static void setSelection(Spinner spinner, String value) {
        if (value == null) return;
        for (int position = 0; position < spinner.getCount(); position++) {
            if (value.equals(spinner.getItemAtPosition(position))) {
                spinner.setSelection(position);
                return;
            }
        }
    }

    private static void setSelection(Spinner spinner, int grade) {
        if (grade > 0 && grade < spinner.getCount()) spinner.setSelection(grade);
    }

    private int dp(int value) {
        return AnalysisViews.dp(getContext(), value);
    }

    private static final class SimpleSelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable action;
        SimpleSelectionListener(Runnable action) { this.action = action; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { action.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
