package com.joellibina.distance;

import android.Manifest;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One daily local sweep per device. WorkManager timing is inexact under Doze. */
public final class ReminderWorker extends Worker {
 private static final String CHANNEL="couple_dates";
 public ReminderWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 @NonNull @Override public Result doWork(){runNow(getApplicationContext());return Result.success();}
 static void schedule(Context c){java.time.ZonedDateTime now=java.time.ZonedDateTime.now();
  java.time.ZonedDateTime next=now.withHour(9).withMinute(0).withSecond(0).withNano(0);if(!next.isAfter(now))next=next.plusDays(1);
  long delay=Math.max(1,java.time.Duration.between(now,next).toMinutes());
  PeriodicWorkRequest req=new PeriodicWorkRequest.Builder(ReminderWorker.class,24,TimeUnit.HOURS).setInitialDelay(delay,TimeUnit.MINUTES).build();
  WorkManager.getInstance(c).enqueueUniquePeriodicWork("couple-date-reminders",ExistingPeriodicWorkPolicy.UPDATE,req);
 }
 static void testNotification(Context c){
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
    android.widget.Toast.makeText(c,"Allow notifications in Android settings first",android.widget.Toast.LENGTH_LONG).show();return;
  }
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Our important dates",NotificationManager.IMPORTANCE_DEFAULT));
  Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.btn_star_big_on)
     .setContentTitle("Joel ♥ Libina").setContentText("Reminders are enabled ♥ This is a test.").setAutoCancel(true).build();
  nm.notify(93301,n);
 }
 static void runNow(Context c){
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Our important dates",NotificationManager.IMPORTANCE_DEFAULT));
  LocalDate today=LocalDate.now();
  for(EventStore.Event e:EventStore.local(c)){
   long days=EventStore.daysUntil(e,today);if(days<0)continue;
   boolean due=false;for(String part:e.reminders.split(","))try{if(Integer.parseInt(part.trim())==days)due=true;}catch(Exception ignored){}
   if(!due)continue;
   String key="date_notice_"+e.id+"_"+today; if(CoupleData.getBool(c,key,false))continue;
   String text=days==0?"Today is "+e.title+" ❤️":e.title+" is coming up in "+days+" day"+(days==1?"":"s")+" ❤️";
   if(e.annual){int years=EventStore.years(e,EventStore.next(e,today));if(years>0)text+=" · "+EventStore.ordinal(years)+" anniversary";}
   Intent open=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.btn_star_big_on).setContentTitle("Joel ♥ Libina").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).build();
   try{nm.notify((e.id+"/"+today).hashCode(),notification);CoupleData.setBool(c,key,true);}catch(SecurityException ignored){}
  }
 }
}
