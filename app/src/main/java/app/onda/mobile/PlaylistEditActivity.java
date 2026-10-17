package app.onda.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class PlaylistEditActivity extends Activity {
    private String oldName, coverUri = "";
    private EditText name, description;
    private ImageView cover;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        oldName = getIntent().getStringExtra("name");
        LibraryStore store = new LibraryStore(this);
        LibraryStore.Playlist playlist = oldName == null ? new LibraryStore.Playlist("", "", "", false) : store.playlist(oldName);
        coverUri = state == null ? playlist.cover : state.getString("cover", playlist.cover);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24)); body.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(body); setContentView(scroll);
        body.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(Ui.dp(this,24)+insets.getSystemWindowInsetLeft(),Ui.dp(this,24)+insets.getSystemWindowInsetTop(),
                    Ui.dp(this,24)+insets.getSystemWindowInsetRight(),Ui.dp(this,24)+insets.getSystemWindowInsetBottom()); return insets;
        });
        Button back = Ui.ghost(this,"Cancelar"); back.setOnClickListener(v->finish()); body.addView(back);
        body.addView(Ui.section(this,oldName == null ? "Uma playlist, do seu jeito" : "Editar playlist"));
        cover = Ui.cover(this,180,24); ((LinearLayout.LayoutParams)cover.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        cover.setContentDescription("Capa da playlist"); body.addView(cover); CoverCache.with(this).load(cover,coverUri);
        Button choose = Ui.ghost(this,"Escolher imagem do aparelho"); choose.setOnClickListener(v ->
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),53));
        body.addView(choose);
        Button clear = Ui.ghost(this,"Usar capa das músicas"); clear.setOnClickListener(v->{coverUri=""; CoverCache.with(this).load(cover,"");}); body.addView(clear);
        body.addView(Ui.body(this,"Nome")); name = input("Nome da playlist",playlist.name,60); body.addView(name);
        body.addView(Ui.body(this,"Descrição")); description = input("Uma história, um clima, um momento…",playlist.description,500);
        description.setSingleLine(false); description.setMinLines(3); description.setGravity(Gravity.TOP); body.addView(description);
        Button save = Ui.primary(this,"Salvar playlist"); body.addView(save);
        save.setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            boolean ok = oldName == null ? store.createPlaylist(value) : store.renamePlaylist(oldName,value);
            if (!ok) { name.setError("Digite um nome único de 1 a 60 caracteres"); return; }
            store.updatePlaylist(value,description.getText().toString().trim(),coverUri,playlist.favorite);
            String url = getIntent().getStringExtra("track_url");
            if (url != null) store.add(LibraryStore.playlistKey(value),new LibraryStore.Track(getIntent().getStringExtra("track_title"),url,
                    getIntent().getStringExtra("track_artist"),getIntent().getStringExtra("track_cover"),getIntent().getLongExtra("track_duration",-1)));
            setResult(RESULT_OK,new Intent().putExtra("name",value)); finish();
        });
    }
    private EditText input(String hint,String text,int max) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setText(text); edit.setTextColor(Color.WHITE); edit.setHintTextColor(Ui.MUTED);
        edit.setTextSize(16); edit.setSingleLine(true); edit.setMinHeight(Ui.dp(this,56));
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)}); return edit;
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=53 || result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException e) { Toast.makeText(this,"Não foi possível manter acesso à imagem",Toast.LENGTH_LONG).show(); return; }
        coverUri=uri.toString(); CoverCache.with(this).load(cover,coverUri);
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("cover",coverUri); }
}
