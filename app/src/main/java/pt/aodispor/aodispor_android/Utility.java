package pt.aodispor.aodispor_android;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public abstract class Utility {

    /**
     * Auxiliary method to convert density independent pixels to actual pixels on the screen
     * depending on the systems metrics.
     * @param dp the number of density independent pixels.
     * @return the number of actual pixels on the screen.
     */
    public static int dpToPx(float dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static String getLastMessageBody(Context ctx, String sender)
    {
        Uri inboxURI = Uri.parse("content://sms/inbox");
        String[] reqCols = new String[] { "_id", "address", "body"};
        ContentResolver cr = ctx.getContentResolver();
        Cursor c = cr.query(inboxURI, reqCols, null, null, null);
        c.moveToFirst();
        while (!c.isLast())
        {
            if (c.getString(1).equals(sender))
            {
                String temp = c.getString(2);
                c.close();
                return temp;
            }
            c.moveToNext();
        }
        return null;
    }
}
