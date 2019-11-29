package com.smlnskgmail.jaman.hashchecker.logic.calculator.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.smlnskgmail.jaman.hashchecker.App;
import com.smlnskgmail.jaman.hashchecker.MainActivity;
import com.smlnskgmail.jaman.hashchecker.R;
import com.smlnskgmail.jaman.hashchecker.components.BaseFragment;
import com.smlnskgmail.jaman.hashchecker.components.dialogs.AppAlertDialog;
import com.smlnskgmail.jaman.hashchecker.components.dialogs.AppProgressDialog;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.functions.HashType;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.functions.task.HashCalculatorTask;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.functions.task.HashCalculatorTaskTarget;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.input.TextInputDialog;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.input.TextValueTarget;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.lists.actions.Action;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.lists.actions.ActionsBottomSheet;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.lists.actions.types.UserActionTarget;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.lists.actions.types.UserActionType;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.lists.hashtypes.GenerateToBottomSheet;
import com.smlnskgmail.jaman.hashchecker.logic.calculator.ui.lists.hashtypes.HashTypeSelectTarget;
import com.smlnskgmail.jaman.hashchecker.logic.filemanager.manager.FileManagerActivity;
import com.smlnskgmail.jaman.hashchecker.logic.filemanager.manager.support.FileRequests;
import com.smlnskgmail.jaman.hashchecker.logic.history.db.HelperFactory;
import com.smlnskgmail.jaman.hashchecker.logic.history.ui.entities.HistoryItem;
import com.smlnskgmail.jaman.hashchecker.logic.settings.SettingsHelper;
import com.smlnskgmail.jaman.hashchecker.tools.LogTool;
import com.smlnskgmail.jaman.hashchecker.tools.UITools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

public class CalculatorFragment extends BaseFragment
        implements TextValueTarget, HashCalculatorTaskTarget, UserActionTarget, HashTypeSelectTarget {

    private static final int TEXT_MULTILINE_LINES_COUNT = 3;
    private static final int TEXT_SINGLE_LINE_LINES_COUNT = 1;

    private View mainScreen;

    private EditText etCustomHash;
    private EditText etGeneratedHash;

    private TextView tvSelectedObjectName;
    private TextView tvSelectedHashType;

    private Button btnGenerateFrom;

    private ProgressDialog progressDialog;

    private Uri fileUri;

    private Context context;
    private FragmentManager fragmentManager;

    private boolean startWithTextSelection;
    private boolean startWithFileSelection;

    private boolean isTextSelected;

    @Override
    public void userActionSelect(@NonNull UserActionType userActionType) {
        switch (userActionType) {
            case ENTER_TEXT:
                enterText();
                break;
            case SEARCH_FILE:
                searchFile();
                break;
            case GENERATE_HASH:
                generateHash();
                break;
            case COMPARE_HASHES:
                compareHashes();
                break;
            case EXPORT_AS_TXT:
                saveGeneratedHashAsTextFile();
                break;
        }
    }

    private void searchFile() {
        if (SettingsHelper.isUsingInnerFileManager(context)) {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermission();
            } else {
                openInnerFileManager();
            }
        } else {
            openSystemFileManager();
        }
    }

    private void openInnerFileManager() {
        Intent openExplorerIntent = new Intent(getContext(), FileManagerActivity.class);
        startActivityForResult(openExplorerIntent, FileRequests.FILE_SELECT_FROM_FILE_MANAGER);
    }

    private void openSystemFileManager() {
        try {
            Intent openExplorerIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openExplorerIntent.addCategory(Intent.CATEGORY_OPENABLE);
            openExplorerIntent.setType("*/*");
            startActivityForResult(openExplorerIntent, FileRequests.FILE_SELECT);
        } catch (ActivityNotFoundException e) {
            LogTool.e(e);
            UITools.showSnackbar(
                    mainScreen.getContext(),
                    mainScreen,
                    getString(R.string.message_error_start_file_selector)
            );
        }
    }

    @SuppressLint("ResourceType")
    private void generateHash() {
        if (fileUri != null || isTextSelected) {
            HashType hashType = HashType.getHashTypeFromString(tvSelectedHashType.getText().toString());
            progressDialog = AppProgressDialog.getDialog(context, R.string.message_generate_dialog);
            progressDialog.show();
            if (isTextSelected) {
                new HashCalculatorTask(
                        hashType,
                        context,
                        tvSelectedObjectName.getText().toString(),
                        this
                ).execute();
            } else {
                new HashCalculatorTask(
                        hashType,
                        context,
                        fileUri,
                        this
                ).execute();
            }
        } else {
            UITools.showSnackbar(context, mainScreen, getString(R.string.message_select_object));
        }
    }

    private void compareHashes() {
        if (TextTools.fieldIsNotEmpty(etCustomHash) && TextTools.fieldIsNotEmpty(etGeneratedHash)) {
            boolean equal = TextTools.compareText(
                    etCustomHash.getText().toString(),
                    etGeneratedHash.getText().toString()
            );
            UITools.showSnackbar(
                    context,
                    mainScreen,
                    getString(equal ? R.string.message_match_result : R.string.message_do_not_match_result)
            );
        } else {
            UITools.showSnackbar(context, mainScreen, getString(R.string.message_fill_fields));
        }
    }

    private void selectHashTypeFromList() {
        GenerateToBottomSheet generateToBottomSheet = new GenerateToBottomSheet();
        generateToBottomSheet.setList(Arrays.asList(HashType.values()));
        generateToBottomSheet.setHashTypeSelectListener(this);
        generateToBottomSheet.show(getFragmentManager());
    }

    private void selectResourceToGenerateHash() {
        showBottomSheetWithActions(Action.TEXT, Action.FILE);
    }

    private void selectActionForHashes() {
        showBottomSheetWithActions(Action.GENERATE, Action.COMPARE, Action.EXPORT_AS_TXT);
    }


    private void saveGeneratedHashAsTextFile() {
        if ((fileUri != null || isTextSelected) && TextTools.fieldIsNotEmpty(etGeneratedHash)) {
            String filename = getString(
                    isTextSelected ? R.string.filename_hash_from_text : R.string.filename_hash_from_file
            );
            saveTextFile(filename);
        } else {
            UITools.showSnackbar(context, mainScreen, getString(R.string.message_generate_hash_before_export));
        }
    }

    private void saveTextFile(@NonNull String filename) {
        try {
            Intent saveTextFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            saveTextFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            saveTextFileIntent.setType("text/plain");
            saveTextFileIntent.putExtra(Intent.EXTRA_TITLE, filename + ".txt");
            startActivityForResult(saveTextFileIntent, FileRequests.FILE_CREATE);
        } catch (ActivityNotFoundException e) {
            LogTool.e(e);
            String errorMessage = getString(R.string.message_error_start_file_selector);
            UITools.showSnackbar(mainScreen.getContext(), mainScreen, errorMessage);
        }
    }

    private void showBottomSheetWithActions(Action... actions) {
        ActionsBottomSheet actionsBottomSheet = new ActionsBottomSheet();
        actionsBottomSheet.setList(Arrays.asList(actions));
        actionsBottomSheet.setUserActionTarget(CalculatorFragment.this);
        actionsBottomSheet.show(fragmentManager);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        if (checkArguments(bundle)) {
            checkShortcutActionPresence(bundle);
        }
    }

    @Override
    public void onPostInitialize() {
        Bundle bundle = getArguments();
        if (checkArguments(bundle)) {
            checkExternalDataPresence(bundle);
        }
    }

    private boolean checkArguments(@Nullable Bundle bundle) {
        return bundle != null;
    }

    private void checkExternalDataPresence(@NonNull Bundle dataArguments) {
        String uri = dataArguments.getString(MainActivity.URI_FROM_EXTERNAL_APP);
        if (uri != null) {
            validateSelectedFile(Uri.parse(uri));
            dataArguments.remove(MainActivity.URI_FROM_EXTERNAL_APP);
        }
    }

    private void checkShortcutActionPresence(@NonNull Bundle shortcutsArguments) {
        startWithTextSelection = shortcutsArguments.getBoolean(App.ACTION_START_WITH_TEXT, false);
        startWithFileSelection = shortcutsArguments.getBoolean(App.ACTION_START_WITH_FILE, false);

        shortcutsArguments.remove(App.ACTION_START_WITH_TEXT);
        shortcutsArguments.remove(App.ACTION_START_WITH_FILE);
    }

    private void validateSelectedFile(@Nullable Uri uri) {
        if (uri != null) {
            fileUri = uri;
            isTextSelected = false;
            setResult(fileNameFromUri(fileUri), false);
        }
    }

    private String fileNameFromUri(@NonNull Uri uri) {
        String scheme = uri.getScheme();
        if (scheme != null && scheme.equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                assert cursor != null;
                cursor.moveToPosition(0);
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            } catch (Exception e) {
                LogTool.e(e);
            }
        }
        return new File(uri.getPath()).getName();
    }

    @Override
    public void textValueEntered(@NonNull String text) {
        setResult(text, true);
    }

    @Override
    public void hashCalculationComplete(@Nullable String hashValue) {
        if (hashValue == null) {
            etGeneratedHash.setText("");
            UITools.showSnackbar(context, mainScreen, getString(R.string.message_invalid_selected_source));
        } else {
            etGeneratedHash.setText(hashValue);
            if (SettingsHelper.canSaveResultToHistory(context)) {
                Date date = Calendar.getInstance().getTime();
                String objectValue = tvSelectedObjectName.getText().toString();
                HashType hashType = HashType.getHashTypeFromString(tvSelectedHashType.getText().toString());
                HistoryItem historyItem = new HistoryItem(date, hashType, !isTextSelected, objectValue, hashValue);
                HelperFactory.getHelper().addHistoryItem(historyItem);
            }
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void setResult(@NonNull String text, boolean isText) {
        tvSelectedObjectName.setText(text);
        this.isTextSelected = isText;
        btnGenerateFrom.setText(getString(isText ? R.string.common_text : R.string.common_file));
    }

    private void enterText() {
        String currentText = !isTextSelected ? null : tvSelectedObjectName.getText().toString();
        new TextInputDialog(context, this, currentText).show();
    }

    @Override
    public void appBackClick() {
        UITools.showSnackbar(context, getView().findViewById(R.id.fl_main_screen),
                getString(R.string.message_exit), getString(R.string.action_exit_now),
                v -> getActivity().finish());
    }

    private void validateTextCase() {
        boolean useUpperCase = SettingsHelper.useUpperCase(context);
        InputFilter[] fieldFilters = useUpperCase
                ? new InputFilter[]{new InputFilter.AllCaps()} : new InputFilter[]{};
        etCustomHash.setFilters(fieldFilters);
        etGeneratedHash.setFilters(fieldFilters);

        if (useUpperCase) {
            TextTools.convertToUpperCase(etCustomHash);
            TextTools.convertToUpperCase(etGeneratedHash);
        } else {
            TextTools.convertToLowerCase(etCustomHash);
            TextTools.convertToLowerCase(etGeneratedHash);
        }

        etCustomHash.setSelection(etCustomHash.getText().length());
        etGeneratedHash.setSelection(etGeneratedHash.getText().length());
    }

    @Override
    public void hashTypeSelect(@NonNull HashType hashType) {
        tvSelectedHashType.setText(hashType.getTypeAsString());
        SettingsHelper.saveHashTypeAsLast(context, hashType);
    }

    @Override
    public void initializeContent(@NonNull View contentView) {
        context = getContext();

        mainScreen = contentView.findViewById(R.id.fl_main_screen);

        etCustomHash = contentView.findViewById(R.id.et_field_custom_hash);
        etGeneratedHash = contentView.findViewById(R.id.et_field_generated_hash);

        tvSelectedObjectName = contentView.findViewById(R.id.tv_selected_object_name);

        tvSelectedHashType = contentView.findViewById(R.id.tv_selected_hash_type);
        tvSelectedHashType.setOnClickListener(v -> selectHashTypeFromList());

        btnGenerateFrom = contentView.findViewById(R.id.btn_generate_from);
        btnGenerateFrom.setOnClickListener(v -> selectResourceToGenerateHash());

        Button btnHashActions = contentView.findViewById(R.id.btn_hash_actions);
        btnHashActions.setOnClickListener(v -> selectActionForHashes());

        fragmentManager = getActivity().getSupportFragmentManager();
        tvSelectedHashType.setText(SettingsHelper.getLastHashType(context).getTypeAsString());
        tvSelectedObjectName.setMovementMethod(new ScrollingMovementMethod());
        if (startWithTextSelection) {
            userActionSelect(UserActionType.ENTER_TEXT);
            startWithTextSelection = false;
        } else if (startWithFileSelection) {
            userActionSelect(UserActionType.SEARCH_FILE);
            startWithFileSelection = false;
        }
    }

    private void requestStoragePermission() {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                FileRequests.PERMISSION_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FileRequests.PERMISSION_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                searchFile();
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    UITools.showSnackbar(context, mainScreen,
                            getString(R.string.message_request_storage_permission_error),
                            getString(R.string.common_again), v -> requestStoragePermission());
                } else {
                    AppAlertDialog.show(context, R.string.title_permission_dialog,
                            R.string.message_request_storage_permission_denied,
                            R.string.menu_title_settings,
                            (dialog, which) -> openAppSettings());
                }
            }
        }
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            LogTool.e(e);
            context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        }
    }

    @Override
    public void appResume() {
        super.appResume();
        validateTextCase();
        checkMultilinePreference();
        checkFileManagerChanged();
        hashTypeSelect(SettingsHelper.getLastHashType(context));
    }

    private void checkMultilinePreference() {
        if (SettingsHelper.isUsingMultilineHashFields(context)) {
            validateMultilineFields(etCustomHash, TEXT_MULTILINE_LINES_COUNT, false);
            validateMultilineFields(etGeneratedHash, TEXT_MULTILINE_LINES_COUNT, false);
        } else {
            validateMultilineFields(etCustomHash, TEXT_SINGLE_LINE_LINES_COUNT, true);
            validateMultilineFields(etGeneratedHash, TEXT_SINGLE_LINE_LINES_COUNT, true);
        }
    }

    private void validateMultilineFields(@NonNull EditText editText, int lines, boolean singleLine) {
        editText.setSingleLine(singleLine);
        editText.setMinLines(lines);
        editText.setMaxLines(lines);
        editText.setLines(lines);
    }

    private void checkFileManagerChanged() {
        if (SettingsHelper.refreshSelectedFile(context)) {
            if (!isTextSelected && fileUri != null) {
                fileUri = null;
                tvSelectedObjectName.setText(getString(R.string.message_select_object));
                btnGenerateFrom.setText(getString(R.string.action_from));
            }
            SettingsHelper.setRefreshSelectedFileStatus(context, false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            switch (requestCode) {
                case FileRequests.FILE_SELECT:
                    if (resultCode == Activity.RESULT_OK) {
                        selectFileFromSystemFileManager(data.getData());
                    }
                    break;
                case FileRequests.FILE_SELECT_FROM_FILE_MANAGER:
                    selectFileFromAppFileManager(data);
                    break;
                case FileRequests.FILE_CREATE:
                    try {
                        writeHashToFile(data.getData());
                    } catch (IOException e) {
                        LogTool.e(e);
                    }
                    break;
            }
        }
    }

    private void selectFileFromSystemFileManager(@Nullable Uri uri) {
        validateSelectedFile(uri);
        SettingsHelper.setGenerateFromShareIntentMode(context, false);
    }

    private void selectFileFromAppFileManager(@NonNull Intent data) {
        String path = data.getStringExtra(FileRequests.FILE_SELECT_DATA);
        if (path != null) {
            Uri uri = Uri.fromFile(new File(path));
            validateSelectedFile(uri);
        }
    }

    private void writeHashToFile(@Nullable Uri uri) throws IOException {
        if (uri != null) {
            ParcelFileDescriptor fileDescriptor = getActivity().getApplicationContext().getContentResolver()
                    .openFileDescriptor(uri, "w");
            if (fileDescriptor != null) {
                FileOutputStream outputStream = new FileOutputStream(fileDescriptor.getFileDescriptor());
                outputStream.write(etGeneratedHash.getText().toString().getBytes());
                outputStream.close();
                fileDescriptor.close();
            }
        }
    }

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_main;
    }

    @Override
    public int getActionBarTitleResId() {
        return R.string.common_app_name;
    }

    @Override
    public int getMenuResId() {
        return R.menu.menu_main;
    }

    @Override
    public boolean setAllowBackAction() {
        return false;
    }

}
