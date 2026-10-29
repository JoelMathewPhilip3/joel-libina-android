package com.joellibina.distance;
import android.app.Activity;
import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.security.SecureRandom;
import java.util.*;

final class FirebaseBridge {
 interface Result { void done(String message); }
 private static ListenerRegistration subscription;
 private static final SecureRandom random=new SecureRandom();
 private static String token(){byte[] b=new byte[20];random.nextBytes(b);StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.US,"%02x",v&255));return s.toString();}
 static String room(Context c){
  String uid=FirebaseAuth.getInstance().getUid();
  if(uid==null)return "";
  String stored=CoupleData.get(c,"roomOwnerUid","");
  return uid.equals(stored)?CoupleData.get(c,"roomId",""):"";
 }
 static void savePair(Context c,String roomId,String invite){
  String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;
  CoupleData.set(c,"roomOwnerUid",uid);
  CoupleData.set(c,"roomId",roomId);
  CoupleData.set(c,"inviteCode",invite);
  FirebaseFirestore.getInstance().collection("users").document(uid)
   .set(Collections.singletonMap("roomId",roomId));
 }
 static void restorePair(Context c){
  String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;
  if(!room(c).isEmpty())return;
  FirebaseFirestore.getInstance().collection("users").document(uid).get()
   .addOnSuccessListener(doc->{String id=doc.getString("roomId");
    if(id!=null&&!id.isEmpty()){
     CoupleData.set(c,"roomOwnerUid",uid); CoupleData.set(c,"roomId",id);
     if(c instanceof Activity)listen((Activity)c);
     else fetch(c);
    }
   });
 }
 static String invite(Context c){return isPaired(c)?CoupleData.get(c,"inviteCode",""):"";}
 static boolean isPaired(Context c){return !room(c).isEmpty();}
 static void sign(Result cb,java.util.function.Consumer<String> next){
  FirebaseAuth auth=FirebaseAuth.getInstance();
  if(auth.getCurrentUser()!=null){next.accept(auth.getCurrentUser().getUid());return;}
  cb.done("Sign in with your email first.");
 }
 static Map<String,Object> shared(Context c){Map<String,Object> m=new HashMap<>();
  for(int n=1;n<=2;n++){String k=Integer.toString(n);m.put("city"+k,CoupleData.get(c,"city"+k,n==1?"Atlanta, Georgia, USA":"Frisco, Texas, USA"));m.put("lat"+k,CoupleData.number(c,"lat"+k,n==1?33.749:33.1507));m.put("lon"+k,CoupleData.number(c,"lon"+k,n==1?-84.388:-96.8236));}
  m.put("reunion",CoupleData.getLong(c,"reunion"));m.put("together",CoupleData.getLong(c,"together"));m.put("anniversary",CoupleData.get(c,"anniversary",""));m.put("sharedMessage","");return m;
 }
 static void apply(Context c,DocumentSnapshot d){ if(!d.exists())return;
  for(int n=1;n<=2;n++){String k=Integer.toString(n);String label=d.getString("city"+k);Double lat=d.getDouble("lat"+k),lon=d.getDouble("lon"+k);if(label!=null&&lat!=null&&lon!=null){CoupleData.set(c,"city"+k,label);CoupleData.set(c,"lat"+k,lat.toString());CoupleData.set(c,"lon"+k,lon.toString());}}
  Long reunion=d.getLong("reunion"),together=d.getLong("together");if(reunion!=null)CoupleData.setLong(c,"reunion",reunion);if(together!=null)CoupleData.setLong(c,"together",together);
  String anniversary=d.getString("anniversary");if(anniversary!=null)CoupleData.set(c,"anniversary",anniversary);
  CoupleData.setLong(c,"lastSync",System.currentTimeMillis());CoupleWidget.refresh(c);
 }
 static void create(Activity a,Result cb){sign(cb,uid->{String id=token(),code=token();FirebaseFirestore db=FirebaseFirestore.getInstance();
  Map<String,Object> data=shared(a);data.put("ownerUid",uid);data.put("partnerUid","");data.put("inviteId",code);
  Map<String,Object> inv=new HashMap<>();inv.put("roomId",id);inv.put("ownerUid",uid);inv.put("joinedUid","");inv.put("expiresAt",System.currentTimeMillis()+24L*60*60*1000);
  WriteBatch b=db.batch();b.set(db.collection("rooms").document(id),data);b.set(db.collection("invites").document(code),inv);
  b.commit().addOnSuccessListener(v->{savePair(a,id,code);EventStore.promotePersonalEvents(a);listen(a);SyncWorker.schedule(a);cb.done("Pair created. Send invitation privately.");}).addOnFailureListener(e->cb.done("Create failed: "+e.getMessage()));
 });}
 static void join(Activity a,String entered,Result cb){String code=entered.trim().toLowerCase(Locale.US);if(!code.matches("[0-9a-f]{40}")){cb.done("Enter the 40-character pairing code.");return;}
  sign(cb,uid->{FirebaseFirestore db=FirebaseFirestore.getInstance();DocumentReference inv=db.collection("invites").document(code);
   inv.get().addOnSuccessListener(s->{if(!s.exists()||!"".equals(s.getString("joinedUid"))||uid.equals(s.getString("ownerUid"))||s.getLong("expiresAt")==null||s.getLong("expiresAt")<System.currentTimeMillis()){cb.done("Invitation unavailable.");return;}
    String id=s.getString("roomId");if(id==null){cb.done("Invalid invitation.");return;}
    WriteBatch b=db.batch();b.update(inv,"joinedUid",uid);b.update(db.collection("rooms").document(id),"partnerUid",uid);
    b.commit().addOnSuccessListener(v->{savePair(a,id,"");EventStore.promotePersonalEvents(a);listen(a);SyncWorker.schedule(a);cb.done("Paired. Both cities now sync.");}).addOnFailureListener(e->cb.done("Join failed: "+e.getMessage()));
   }).addOnFailureListener(e->cb.done("Invitation lookup failed: "+e.getMessage()));
  });
 }
 static void listen(Activity a){stop();if(!isPaired(a))return;EventStore.start(a);sign(s->{},uid->{subscription=FirebaseFirestore.getInstance().collection("rooms").document(room(a)).addSnapshotListener((d,e)->{if(e==null&&d!=null)apply(a,d);});});}
 static void fetch(Context c){EventStore.fetch(c);if(!isPaired(c)||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  FirebaseFirestore.getInstance().collection("rooms").document(room(c)).get(Source.SERVER).addOnSuccessListener(d->apply(c,d));
 }
 static void syncShared(Activity a,Result cb){if(!isPaired(a)){cb.done("Pair phones first.");return;}
  sign(cb,uid->FirebaseFirestore.getInstance().collection("rooms").document(room(a)).update(shared(a)).addOnSuccessListener(v->{CoupleData.setLong(a,"lastSync",System.currentTimeMillis());CoupleWidget.refresh(a);cb.done("Shared settings uploaded.");}).addOnFailureListener(e->cb.done("Sync failed: "+e.getMessage())));
 }
 static void stop(){EventStore.stop();if(subscription!=null){subscription.remove();subscription=null;}}
}
