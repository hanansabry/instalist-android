package org.noorganization.instalist.view.utils;

import android.widget.EditText;

/**
 * Created by tinos_000 on 18.05.2015.
 */
public class ViewUtils {

    /**
     * Checks if the given textview is filled with some text. If it is not filled then there will be an message be shown
     * @param _TextView the textview that should be tested.
     * @return
     */
    public static boolean checkTextViewIsFilled(EditText _TextView){
        if(_TextView.length() == 0
                || (_TextView.getText().toString().replaceAll("(\\s)*","").length() == 0) ){

            _TextView.setError("Not filled");
            return false;
        }

        _TextView.setError(null);
        return true;
    }}
