package com.comleoneo.myrestaurant;

import android.Manifest;
import android.accounts.Account;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.comleoneo.myrestaurant.Common.Common;
import com.comleoneo.myrestaurant.Retrofit.IMyRestaurantAPI;
import com.comleoneo.myrestaurant.Retrofit.RetrofitClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import dmax.dialog.SpotsDialog;
import io.paperdb.Paper;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class SplashScreen extends AppCompatActivity {

    private static final String TAG = SplashScreen.class.getSimpleName();

    private IMyRestaurantAPI mIMyRestaurantAPI;
    private CompositeDisposable mCompositeDisposable = new CompositeDisposable();
    private AlertDialog mDialog;

    @Override
    protected void onDestroy() {
        mCompositeDisposable.clear();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: started!!");

        init();
        Dexter.withContext(this)
                .withPermissions(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                            // Get Token
                            FirebaseInstanceId.getInstance()
                                    .getInstanceId()
                                    .addOnFailureListener(e -> Toast.makeText(SplashScreen.this, "[GET TOKEN]"+e.getMessage(), Toast.LENGTH_SHORT).show())
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                            if (user!= null) {
                                                Paper.book().write(Common.REMEMBER_FBID, user.getUid());

                                                mDialog.show();

                                                // After we have account from facebook, we will get fbid and update token
                                                mCompositeDisposable.add(mIMyRestaurantAPI.updateTokenToServer(Common.API_KEY,
                                                                user.getUid(), task.getResult().getToken())
                                                        .subscribeOn(Schedulers.io())
                                                        .observeOn(AndroidSchedulers.mainThread())
                                                        .subscribe(tokenModel -> {

                                                            if (!tokenModel.isSuccess()) {
                                                                Toast.makeText(SplashScreen.this, "[UPDATE TOKEN ERROR]", Toast.LENGTH_SHORT).show();
                                                                return;
                                                            }

                                                            mCompositeDisposable.add(mIMyRestaurantAPI.getUser(Common.API_KEY, user.getUid())
                                                                    .subscribeOn(Schedulers.io())
                                                                    .observeOn(AndroidSchedulers.mainThread())
                                                                    .subscribe(userModel -> {

                                                                        // If user available in database
                                                                        if (userModel.isSuccess()) {
                                                                            Common.currentUser = userModel.getResult().get(0);
                                                                            Intent intent = new Intent(SplashScreen.this, HomeActivity.class);
                                                                            startActivity(intent);
                                                                            finish();
                                                                        }
                                                                        // If user not available in database, start UpdateInformation for register
                                                                        else {
                                                                            Intent intent = new Intent(SplashScreen.this, UpdateInfoActivity.class);
                                                                            startActivity(intent);
                                                                            finish();
                                                                        }

                                                                        mDialog.dismiss();

                                                                    }, throwable -> {
                                                                        mDialog.dismiss();
                                                                        Toast.makeText(SplashScreen.this, "[GET USER API]" + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                                                                        Log.d(TAG, "[GET USER API]" + throwable.getMessage());
                                                                    }));

                                                        }, throwable -> {
                                                            Toast.makeText(SplashScreen.this, "[UPDATE TOKEN]"+throwable.getMessage(), Toast.LENGTH_SHORT).show();
                                                        }));
                                            }else {
                                                Toast.makeText(SplashScreen.this, "Not sign in. Please sign in", Toast.LENGTH_SHORT).show();
                                                startActivity(new Intent(SplashScreen.this, MainActivity.class));
                                                finish();
                                            }
                                        }
                                    });
                        }
                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        Toast.makeText(SplashScreen.this, "ERROR", Toast.LENGTH_SHORT).show();
                    }
                }).check();
        //printKeyHash();

//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                startActivity(new Intent(SplashScreen.this, MainActivity.class));
//                finish();
//            }
//        }, 2000);

    }

    private void init() {
        Log.d(TAG, "init: called!!");
        Paper.init(this);
        mDialog = new SpotsDialog.Builder().setContext(this).setCancelable(false).build();
        mIMyRestaurantAPI = RetrofitClient.getInstance(Common.API_RESTAURANT_ENDPOINT)
                .create(IMyRestaurantAPI.class);
    }

    private void printKeyHash() {
        Log.d(TAG, "printKeyHash: called!!");

        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d(TAG, "printKeyHash: " + Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
