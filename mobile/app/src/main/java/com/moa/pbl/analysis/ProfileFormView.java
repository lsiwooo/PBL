package com.moa.pbl.analysis;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 또래 비교에 필요한 프로필(이름·성별·나이·학교·학년·지역) 입력 폼 */
public final class ProfileFormView extends LinearLayout {

    public interface OnSaved {
        void onSaved(PeerProfile profile);
    }

    private static final int BLUE = 0xff1b4fa3;
    private static final int TEXT = 0xff172642;
    private static final int BORDER = 0xffdfe5dc;

    private final EditText nameInput;
    private final EditText schoolInput;
    private final Spinner ageSpinner;
    private final Spinner regionSpinner;
    private final List<TextView> genderChips = new ArrayList<>();
    private final List<TextView> gradeChips = new ArrayList<>();
    private String gender;
    private int grade;

    public ProfileFormView(Context context, @Nullable PeerProfile initial, OnSaved onSaved) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(18), dp(16), dp(18), dp(16));
        setBackground(AnalysisViews.rounded(context, 0xffffffff, 24, 0xffe7e8e8));

        label("이름");
        nameInput = input("이름", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);

        label("성별");
        LinearLayout genderRow = chipRow();
        for (String value : new String[]{PeerProfile.MALE, PeerProfile.FEMALE}) {
            TextView chip = chip(value);
            chip.setOnClickListener(v -> selectGender(value));
            genderChips.add(chip);
            genderRow.addView(chip, chipParams());
        }

        label("나이");
        List<String> ages = new ArrayList<>();
        ages.add("선택하세요");
        for (int age = PeerProfile.MIN_AGE; age <= PeerProfile.MAX_AGE; age++) ages.add(age + "세");
        ageSpinner = spinner(ages);

        label("학교");
        schoolInput = input("예: 한국대학교", InputType.TYPE_CLASS_TEXT);

        label("학년");
        LinearLayout gradeRow = chipRow();
        for (int value = 1; value <= 4; value++) {
            final int selected = value;
            TextView chip = chip(value + "학년");
            chip.setOnClickListener(v -> selectGrade(selected));
            gradeChips.add(chip);
            gradeRow.addView(chip, chipParams());
        }

        label("지역");
        List<String> regions = new ArrayList<>();
        regions.add("선택하세요");
        for (String region : PeerProfile.REGIONS) regions.add(region);
        regionSpinner = spinner(regions);

        Button save = new Button(context);
        save.setText("저장하고 또래와 비교하기");
        save.setAllCaps(false);
        save.setTextSize(15);
        save.setTextColor(0xffffffff);
        save.setBackground(AnalysisViews.rounded(context, BLUE, 14, BLUE));
        LayoutParams saveParams = new LayoutParams(-1, dp(52));
        saveParams.setMargins(0, dp(16), 0, 0);
        addView(save, saveParams);
        save.setOnClickListener(v -> submit(onSaved));

        if (initial != null) {
            nameInput.setText(initial.name);
            schoolInput.setText(initial.school);
            selectGender(initial.gender);
            selectGrade(initial.grade);
            ageSpinner.setSelection(initial.age - PeerProfile.MIN_AGE + 1);
            regionSpinner.setSelection(indexOf(PeerProfile.REGIONS, initial.region) + 1);
        }
    }

    private void submit(OnSaved onSaved) {
        String name = nameInput.getText().toString().trim();
        String school = schoolInput.getText().toString().trim();
        int agePosition = ageSpinner.getSelectedItemPosition();
        int regionPosition = regionSpinner.getSelectedItemPosition();
        if (name.isEmpty() || school.isEmpty() || gender == null || grade == 0
                || agePosition == 0 || regionPosition == 0) {
            Toast.makeText(getContext(), "모든 항목을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        onSaved.onSaved(new PeerProfile(name, gender, PeerProfile.MIN_AGE + agePosition - 1,
                school, grade, PeerProfile.REGIONS[regionPosition - 1]));
    }

    private void selectGender(String value) {
        gender = value;
        String[] values = {PeerProfile.MALE, PeerProfile.FEMALE};
        for (int i = 0; i < genderChips.size(); i++) styleChip(genderChips.get(i), values[i].equals(value));
    }

    private void selectGrade(int value) {
        grade = value;
        for (int i = 0; i < gradeChips.size(); i++) styleChip(gradeChips.get(i), i + 1 == value);
    }

    private void label(String value) {
        TextView view = AnalysisViews.text(getContext(), value, 13, 0xff7e8798, true);
        view.setPadding(0, dp(10), 0, dp(4));
        addView(view);
    }

    private EditText input(String hint, int inputType) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(AnalysisViews.rounded(getContext(), 0xffffffff, 14, BORDER));
        addView(input, new LayoutParams(-1, -2));
        return input;
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

    private LinearLayout chipRow() {
        LinearLayout row = new LinearLayout(getContext());
        addView(row, new LayoutParams(-1, -2));
        return row;
    }

    private TextView chip(String value) {
        TextView chip = new TextView(getContext());
        chip.setText(value);
        chip.setTextSize(14);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(0, dp(10), 0, dp(10));
        styleChip(chip, false);
        return chip;
    }

    private LayoutParams chipParams() {
        LayoutParams params = new LayoutParams(0, -2, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? 0xffffffff : TEXT);
        chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setBackground(AnalysisViews.rounded(getContext(), selected ? BLUE : 0xffffffff, 14,
                selected ? BLUE : BORDER));
    }

    private int dp(int value) {
        return AnalysisViews.dp(getContext(), value);
    }

    private static int indexOf(String[] items, String value) {
        for (int i = 0; i < items.length; i++) if (items[i].equals(value)) return i;
        return -1;
    }
}
