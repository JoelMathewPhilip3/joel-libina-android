package com.joellibina.distance;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class DailyReceiver extends BroadcastReceiver {
    static final String CHANNEL="couple_anniversary";
    static void schedule(Context c) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent intent=new Intent(c,DailyReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,4,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+AlarmManager.INTERVAL_DAY,AlarmManager.INTERVAL_DAY,pi);
    }
    @Override public void onReceive(Context c,Intent i) {
        CoupleWidget.refresh(c);
        if(!CoupleData.getBool(c,"reminders",false))return;
        String anniversary=CoupleData.anniversary(c);
        if(!anniversary.equals("Happy anniversary ♥"))return;
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Anniversary reminders",NotificationManager.IMPORTANCE_DEFAULT));
        Notification n=new Notification.Builder(c,CHANNEL).setContentTitle("Joel ♥ Libina").setContentText("Happy anniversary ♥").setSmallIcon(android.R.drawable.btn_star_big_on).setAutoCancel(true).build();
        try {nm.notify(11,n);}catch(SecurityException ignored){}
    }
}
