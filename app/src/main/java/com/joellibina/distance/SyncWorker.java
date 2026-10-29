package com.joellibina.distance;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public final class SyncWorker extends Worker {
 public SyncWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 @NonNull @Override public Result doWork(){FirebaseBridge.fetch(getApplicationContext());CoupleWidget.refresh(getApplicationContext());return Result.success();}
 static void schedule(Context c){Constraints net=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  PeriodicWorkRequest work=new PeriodicWorkRequest.Builder(SyncWorker.class,new long[]{240,120,30}[Math.max(0,Math.min(2,(int)CoupleData.number(c,"batteryProfile",1)))],TimeUnit.MINUTES).setConstraints(net).build();
  WorkManager.getInstance(c).enqueueUniquePeriodicWork("couple-shared-refresh",ExistingPeriodicWorkPolicy.UPDATE,work);
 }
}
