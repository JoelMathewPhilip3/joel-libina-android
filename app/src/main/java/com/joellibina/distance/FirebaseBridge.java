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
 private static volatile String partnerUid="";
 private static volatile boolean roomVerified=false;
 private static volatile long inviteExpiresAt=0;
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
 static boolean hasPartner(){return !partnerUid.isEmpty();}
 static boolean roomVerified(){return roomVerified;}
 static long inviteExpiry(){return inviteExpiresAt;}
 static void inspectRoom(Context c,DocumentSnapshot d){
  if(!d.exists())return;
  String partner=d.getString("partnerUid");partnerUid=partner==null?"":partner;roomVerified=true;
  String owner=d.getString("ownerUid");String uid=FirebaseAuth.getInstance().getUid();
  if(uid!=null&&uid.equals(owner)&&partnerUid.isEmpty()){
   String code=d.getString("inviteId");
   if(code!=null&&!code.isEmpty()){
    CoupleData.set(c,"inviteCode",code);
    FirebaseFirestore.getInstance().collection("invites").document(code).get(Source.SERVER)
     .addOnSuccessListener(inv->{Long ex=inv.getLong("expiresAt");inviteExpiresAt=ex==null?0:ex;
      if(c instanceof MainActivity && !((Activity)c).isFinishing())((Activity)c).runOnUiThread(()->((MainActivity)c).build());
     });
   }
  }else {CoupleData.set(c,"inviteCode","");inviteExpiresAt=0;}
  if(c instanceof MainActivity && !((Activity)c).isFinishing())((Activity)c).runOnUiThread(()->((MainActivity)c).build());
 }
 static void renewInvite(Activity a,Result cb){
  sign(cb,uid->{String id=room(a);if(id.isEmpty()){cb.done("No room found. Sign in again.");return;}
   FirebaseFirestore db=FirebaseFirestore.getInstance();DocumentReference roomDoc=db.collection("rooms").document(id);
   roomDoc.get(Source.SERVER).addOnSuccessListener(doc->{
    if(!doc.exists()||!uid.equals(doc.getString("ownerUid"))||!"".equals(doc.getString("partnerUid"))){cb.done("Only the owner of an unjoined room can renew its invitation.");return;}
    String code=token();Map<String,Object> inv=new HashMap<>();inv.put("roomId",id);inv.put("ownerUid",uid);inv.put("joinedUid","");inv.put("expiresAt",System.currentTimeMillis()+86400000L);
    WriteBatch b=db.batch();b.update(roomDoc,"inviteId",code);b.set(db.collection("invites").document(code),inv);
    b.commit().addOnSuccessListener(v->{CoupleData.set(a,"inviteCode",code);inviteExpiresAt=(Long)inv.get("expiresAt");listen(a);cb.done("New invitation ready. Your room was kept.");})
      .addOnFailureListener(e->cb.done("Renewal failed: "+e.getMessage()));
   }).addOnFailureListener(e->cb.done("Room check failed: "+e.getMessage()));
  });
 }
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
 static void create(Activity a,Result cb){sign(cb,uid->{
  if(!room(a).isEmpty()){cb.done("Already linked to a room. Refresh or renew its invitation.");return;}
  FirebaseFirestore db=FirebaseFirestore.getInstance();
  db.collection("users").document(uid).get(Source.SERVER).addOnSuccessListener(existing->{
   String attached=existing.getString("roomId");
   if(attached!=null&&!attached.isEmpty()){
    CoupleData.set(a,"roomOwnerUid",uid);CoupleData.set(a,"roomId",attached);listen(a);
    cb.done("Existing room restored. No new room created.");return;
   }
   String id=token(),code=token();
  Map<String,Object> data=shared(a);data.put("ownerUid",uid);data.put("partnerUid","");data.put("inviteId",code);
  Map<String,Object> inv=new HashMap<>();inv.put("roomId",id);inv.put("ownerUid",uid);inv.put("joinedUid","");inv.put("expiresAt",System.currentTimeMillis()+24L*60*60*1000);
  WriteBatch b=db.batch();b.set(db.collection("rooms").document(id),data);b.set(db.collection("invites").document(code),inv);
  b.commit().addOnSuccessListener(v->{savePair(a,id,code);EventStore.promotePersonalEvents(a);listen(a);SyncWorker.schedule(a);cb.done("Pair created. Send invitation privately.");}).addOnFailureListener(e->cb.done("Create failed: "+e.getMessage()));
  }).addOnFailureListener(e->cb.done("Cannot verify existing room: "+e.getMessage()));
 });}
 static void join(Activity a,String entered,Result cb){String code=entered.trim().toLowerCase(Locale.US);if(!code.matches("[0-9a-f]{40}")){cb.done("Enter the 40-character pairing code.");return;}
  sign(cb,uid->{FirebaseFirestore db=FirebaseFirestore.getInstance();DocumentReference inv=db.collection("invites").document(code);
   inv.get().addOnSuccessListener(s->{if(!s.exists()||!"".equals(s.getString("joinedUid"))||uid.equals(s.getString("ownerUid"))||s.getLong("expiresAt")==null||s.getLong("expiresAt")<System.currentTimeMillis()){cb.done("Invitation unavailable.");return;}
    String id=s.getString("roomId");if(id==null){cb.done("Invalid invitation.");return;}
    if(!room(a).isEmpty()){cb.done("This account already belongs to a room.");return;}
    WriteBatch b=db.batch();b.update(inv,"joinedUid",uid);b.update(db.collection("rooms").document(id),"partnerUid",uid);
    b.commit().addOnSuccessListener(v->{savePair(a,id,"");EventStore.promotePersonalEvents(a);listen(a);SyncWorker.schedule(a);cb.done("Paired. Both cities now sync.");}).addOnFailureListener(e->cb.done("Join failed: "+e.getMessage()));
   }).addOnFailureListener(e->cb.done("Invitation lookup failed: "+e.getMessage()));
  });
 }
 static void listen(Activity a){stop();if(!isPaired(a))return;roomVerified=false;EventStore.start(a);sign(s->{},uid->{subscription=FirebaseFirestore.getInstance().collection("rooms").document(room(a)).addSnapshotListener((d,e)->{if(e==null&&d!=null&&d.exists()){apply(a,d);inspectRoom(a,d);}});});}
 static void fetch(Context c){EventStore.fetch(c);if(!isPaired(c)||FirebaseAuth.getInstance().getCurrentUser()==null)return;
  FirebaseFirestore.getInstance().collection("rooms").document(room(c)).get(Source.SERVER).addOnSuccessListener(d->{apply(c,d);inspectRoom(c,d);});
 }
 static void syncShared(Activity a,Result cb){if(!isPaired(a)){cb.done("Pair phones first.");return;}
  sign(cb,uid->FirebaseFirestore.getInstance().collection("rooms").document(room(a)).update(shared(a)).addOnSuccessListener(v->{CoupleData.setLong(a,"lastSync",System.currentTimeMillis());CoupleWidget.refresh(a);cb.done("Shared settings uploaded.");}).addOnFailureListener(e->cb.done("Sync failed: "+e.getMessage())));
 }
 static void stop(){EventStore.stop();if(subscription!=null){subscription.remove();subscription=null;}}
}
