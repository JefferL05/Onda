package app.onda.mobile;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Bounded image pipeline, including persisted document covers and MediaStore album art. */
public final class CoverCache {
    private static volatile CoverCache instance;
    private final Context context;
    private final LruCache<String,Bitmap> memory = new LruCache<String,Bitmap>(8*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private final Map<String,List<WeakReference<ImageView>>> pending = new HashMap<>();
    private final Map<String,Long> failures = new LinkedHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File directory;
    private CoverCache(Context context) {
        this.context=context; directory=new File(context.getCacheDir(),"covers"); directory.mkdirs();
    }
    public static CoverCache with(Context context) {
        if(instance==null) synchronized(CoverCache.class) { if(instance==null) instance=new CoverCache(context.getApplicationContext()); }
        return instance;
    }
    public void load(ImageView view,String raw) {
        String url=raw==null?"":raw;
        Bitmap cached=memory.get(url);
        if(url.equals(view.getTag()) && cached!=null) return;
        view.setTag(url);
        if(cached!=null) { view.setImageBitmap(cached); return; }
        view.setImageResource(R.drawable.ic_onda);
        if(url.isEmpty() || (!url.startsWith("content://") && UrlPolicy.host(url).isEmpty())) return;
        if(!url.startsWith("content://") && dataSaverBlocks()) return; // Metered network + data saver: skip covers.
        synchronized(pending) {
            if(SystemClock.elapsedRealtime()-failures.getOrDefault(url,-60000L)<30000) return;
            List<WeakReference<ImageView>> waiting=pending.get(url);
            if(waiting!=null) {
                for(WeakReference<ImageView> ref:waiting) if(ref.get()==view) return;
                waiting.add(new WeakReference<>(view)); return;
            }
            waiting=new ArrayList<>(); waiting.add(new WeakReference<>(view)); pending.put(url,waiting);
        }
        workers.execute(()->{
            Bitmap bitmap=null;
            try { bitmap=read(url); } catch(Exception ignored) { }
            Bitmap result=bitmap;
            if(result!=null) memory.put(url,result);
            main.post(()->{
                List<WeakReference<ImageView>> waiting;
                synchronized(pending) {
                    waiting=pending.remove(url);
                    if(result==null) { if(failures.size()>100) failures.clear(); failures.put(url,SystemClock.elapsedRealtime()); }
                }
                if(waiting!=null && result!=null) for(WeakReference<ImageView> ref:waiting) {
                    ImageView target=ref.get(); if(target!=null && url.equals(target.getTag())) target.setImageBitmap(result);
                }
            });
        });
    }
    private Bitmap read(String url) throws Exception {
        File cache=new File(directory,key(url)+".jpg");
        if(cache.isFile()) {
            Bitmap bitmap=decode(java.nio.file.Files.readAllBytes(cache.toPath()));
            if(bitmap!=null) { cache.setLastModified(System.currentTimeMillis()); return bitmap; }
            cache.delete();
        }
        byte[] data;
        if(url.startsWith("content://")) {
            try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(url))) { data=bytes(in); }
        } else {
            HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
            try {
                if(connection.getResponseCode()!=200) return null;
                String mime=connection.getContentType(); if(mime==null || !mime.startsWith("image/")) return null;
                try(InputStream in=connection.getInputStream()) { data=bytes(in); }
            } finally { connection.disconnect(); }
        }
        Bitmap bitmap=decode(data); if(bitmap==null) return null;
        File temp=File.createTempFile("cover-",".part",directory);
        try(FileOutputStream out=new FileOutputStream(temp)) { bitmap.compress(Bitmap.CompressFormat.JPEG,85,out); }
        if(!temp.renameTo(cache)) temp.delete();
        trim(); return bitmap;
    }
    private byte[] bytes(InputStream in) throws IOException {
        if(in==null) throw new IOException("Imagem indisponível");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[16384]; int n;
        while((n=in.read(buffer))!=-1) {
            if(bytes.size()+n>5*1024*1024) throw new IOException("Imagem muito grande"); bytes.write(buffer,0,n);
        }
        return bytes.toByteArray();
    }
    private Bitmap decode(byte[] data) {
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,options);
        options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>640 || options.outHeight/options.inSampleSize>640) options.inSampleSize*=2;
        options.inJustDecodeBounds=false; return BitmapFactory.decodeByteArray(data,0,data.length,options);
    }
    private void trim() {
        File[] files=directory.listFiles((dir,name)->name.endsWith(".jpg")); if(files==null) return;
        long total=0; for(File file:files) total+=file.length(); if(total<=64L*1024*1024) return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        for(File file:files) { long size=file.length(); if(file.delete()) total-=size; if(total<=48L*1024*1024) break; }
    }
    private String key(String url) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder(); for(byte b:hash) result.append(String.format(Locale.ROOT,"%02x",b)); return result.toString();
    }
    private boolean dataSaverBlocks() {
        try {
            if(!context.getSharedPreferences("playback",Context.MODE_PRIVATE).getBoolean("dataSaver",false)) return false;
            android.net.ConnectivityManager networks=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            return networks!=null && networks.isActiveNetworkMetered();
        } catch(Exception ignored) { return false; }
    }
}
