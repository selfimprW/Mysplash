package com.wangdaye.mysplash.common.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.wangdaye.mysplash.Mysplash;
import com.wangdaye.mysplash.R;
import com.wangdaye.mysplash.common.basic.adapter.FooterAdapter;
import com.wangdaye.mysplash.common.network.json.Collection;
import com.wangdaye.mysplash.common.network.json.Photo;
import com.wangdaye.mysplash.common.network.json.User;
import com.wangdaye.mysplash.common.basic.activity.MysplashActivity;
import com.wangdaye.mysplash.common.ui.dialog.DownloadRepeatDialog;
import com.wangdaye.mysplash.common.ui.widget.CircleImageView;
import com.wangdaye.mysplash.common.ui.widget.CircularProgressIcon;
import com.wangdaye.mysplash.common.utils.DisplayUtils;
import com.wangdaye.mysplash.common.utils.FileUtils;
import com.wangdaye.mysplash.common.download.NotificationHelper;
import com.wangdaye.mysplash.common.db.DatabaseHelper;
import com.wangdaye.mysplash.common.image.ImageHelper;
import com.wangdaye.mysplash.common.utils.helper.IntentHelper;
import com.wangdaye.mysplash.common.utils.manager.AuthManager;
import com.wangdaye.mysplash.common.ui.dialog.DeleteCollectionPhotoDialog;
import com.wangdaye.mysplash.common.ui.dialog.SelectCollectionDialog;
import com.wangdaye.mysplash.common.ui.widget.freedomSizeView.FreedomImageView;
import com.wangdaye.mysplash.collection.ui.CollectionActivity;
import com.wangdaye.mysplash.common.utils.manager.ThreadManager;
import com.wangdaye.mysplash.user.ui.UserActivity;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Photo adapter.
 *
 * Adapter for {@link RecyclerView} to show photos.
 *
 * */

public class PhotoAdapter extends FooterAdapter<RecyclerView.ViewHolder>
        implements PhotoHolder.ParentAdapter {

    private List<Photo> itemList;

    private int columnCount;
    private boolean showDeleteButton;

    @Nullable private ItemEventCallback callback;

    public PhotoAdapter(Context context, List<Photo> list, int columnCount) {
        super(context);
        this.itemList = list;
        this.columnCount = columnCount;
        this.showDeleteButton = false;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        if (isFooter(position)) {
            // footer.
            return FooterHolder.buildInstance(parent);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo, parent, false);
            return new PhotoHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PhotoHolder && position < getRealItemCount()) {
            ((PhotoHolder) holder).onBindView(
                    itemList.get(position), showDeleteButton, columnCount, false,
                    callback, this);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            ((PhotoHolder) holder).onBindView(
                    itemList.get(position), showDeleteButton, columnCount, true,
                    callback, this);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof PhotoHolder) {
            ((PhotoHolder) holder).onRecycled();
        }
    }

    @Override
    public int getRealItemCount() {
        return itemList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    protected boolean hasFooter(Context context) {
        return !DisplayUtils.isLandscape(context)
                && DisplayUtils.getNavigationBarHeight(context.getResources()) != 0;
    }

    public void insertItemToFirst(Photo item) {
        if (item.width != 0 && item.height != 0) {
            itemList.add(0, item);
            notifyItemInserted(0);
        }
    }

    public void removeItem(Photo item) {
        for (int i = 0; i < itemList.size(); i ++) {
            if (itemList.get(i).id .equals(item.id)) {
                itemList.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public void setShowDeleteButton(boolean showDeleteButton) {
        this.showDeleteButton = showDeleteButton;
        notifyDataSetChanged();
    }

    public void updatePhoto(RecyclerView recyclerView,
                            Photo newPhoto, boolean refreshView, boolean probablyRepeat) {
        ThreadManager.getInstance().execute(() -> {
            for (int i = 0; i < itemList.size(); i ++) {
                if (itemList.get(i).id.equals(newPhoto.id)) {
                    newPhoto.loadPhotoSuccess = itemList.get(i).loadPhotoSuccess;
                    newPhoto.hasFadedIn = itemList.get(i).hasFadedIn;
                    newPhoto.settingLike = false;
                    itemList.set(i, newPhoto);
                    if (refreshView) {
                        int finalI = i;
                        recyclerView.post(() -> notifyItemChanged(finalI, 1));
                    }
                    if (!probablyRepeat) {
                        return;
                    }
                }
            }
        });
    }

    public void setPhotoData(List<Photo> list) {
        itemList = list;
        notifyDataSetChanged();
    }

    public List<Photo> getPhotoData() {
        return itemList;
    }

    // interface.

    public interface ItemEventCallback {
        void onLikeOrDislikePhoto(Photo photo, int adapterPosition, boolean setToLike);
        void onDownload(Photo photo);
    }

    public void setItemEventCallback(@Nullable ItemEventCallback c) {
        this.callback = c;
    }

    // parent adapter.

    @Override
    public void startPhotoActivity(View image, View background, int adapterPosition) {
        MysplashActivity activity = Mysplash.getInstance().getTopActivity();
        if (activity != null) {
            ArrayList<Photo> list = new ArrayList<>();
            int headIndex = adapterPosition - 2;
            int size = 5;
            if (headIndex < 0) {
                headIndex = 0;
            }
            if (headIndex + size - 1 > itemList.size() - 1) {
                size = itemList.size() - headIndex;
            }
            for (int i = headIndex; i < headIndex + size; i ++) {
                list.add(itemList.get(i));
            }

            IntentHelper.startPhotoActivity(
                    activity, image, background, list, adapterPosition, headIndex);
        }
    }

    @Override
    public void createCollectResult(Collection collection) {
        Mysplash.getInstance().dispatchCollectionUpdate(collection, Mysplash.MessageType.CREATE);

        User user = AuthManager.getInstance().getUser();
        if (user != null) {
            user.total_collections ++;
            Mysplash.getInstance().dispatchUserUpdate(user, Mysplash.MessageType.UPDATE);
        }
    }

    @Override
    public void updateCollectResult(Photo photo, Collection collection, User user) {
        Mysplash.getInstance().dispatchPhotoUpdate(photo, Mysplash.MessageType.UPDATE);
        Mysplash.getInstance().dispatchCollectionUpdate(collection, Mysplash.MessageType.UPDATE);
        Mysplash.getInstance().dispatchUserUpdate(user, Mysplash.MessageType.UPDATE);
    }

    @Override
    public void removePhotoFromCollectionResult(Photo photo, Collection collection, User user) {
        removeItem(photo);
        Mysplash.getInstance().dispatchPhotoUpdate(photo, Mysplash.MessageType.UPDATE);
        Mysplash.getInstance().dispatchCollectionUpdate(collection, Mysplash.MessageType.UPDATE);
        Mysplash.getInstance().dispatchUserUpdate(user, Mysplash.MessageType.UPDATE);
    }
}

class PhotoHolder extends RecyclerView.ViewHolder {

    @BindView(R.id.item_photo) CardView card;
    @BindView(R.id.item_photo_image) FreedomImageView image;

    @BindView(R.id.item_photo_avatar) CircleImageView avatar;
    @BindView(R.id.item_photo_title) TextView title;

    @BindView(R.id.item_photo_deleteButton) AppCompatImageButton deleteButton;

    @BindView(R.id.item_photo_collectionButton) AppCompatImageButton collectionButton;
    @BindView(R.id.item_photo_likeButton) CircularProgressIcon likeButton;

    private Photo photo;
    @Nullable private PhotoAdapter.ItemEventCallback callback;
    @Nullable private ParentAdapter parentAdapter;

    PhotoHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    void onBindView(Photo photo, boolean showDeleteButton, int columnCount, boolean update,
                    @Nullable PhotoAdapter.ItemEventCallback callback, @NonNull ParentAdapter adapter) {
        Context context = itemView.getContext();

        this.photo = photo;
        this.callback = callback;
        this.parentAdapter = adapter;

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        if (columnCount > 1) {
            int margin = context.getResources().getDimensionPixelSize(R.dimen.normal_margin);
            params.setMargins(0, 0, margin, margin);
            card.setLayoutParams(params);
            card.setRadius(context.getResources().getDimensionPixelSize(R.dimen.material_card_radius));
        } else {
            params.setMargins(0, 0, 0, 0);
            card.setLayoutParams(params);
            card.setRadius(0);
        }

        image.setSize(photo.width, photo.height);

        if (!update) {
            ImageHelper.loadAvatar(avatar.getContext(), avatar, photo.user, null);

            title.setText("");
            image.setShowShadow(false);

            ImageHelper.loadRegularPhoto(image.getContext(), image, photo, () -> {
                photo.loadPhotoSuccess = true;
                if (!photo.hasFadedIn) {
                    photo.hasFadedIn = true;
                    ImageHelper.startSaturationAnimation(image.getContext(), image);
                }
                title.setText(photo.user.name);
                image.setShowShadow(true);
            });
        }

        if (showDeleteButton) {
            deleteButton.setVisibility(View.VISIBLE);
        } else {
            deleteButton.setVisibility(View.GONE);
        }

        if (photo.current_user_collections.size() != 0) {
            collectionButton.setImageResource(R.drawable.ic_item_collected);
        } else {
            collectionButton.setImageResource(R.drawable.ic_item_collect);
        }

        likeButton.setProgressColor(Color.WHITE);
        if (photo.settingLike) {
            likeButton.setProgressState();
        } else {
            likeButton.setResultState(
                    photo.liked_by_user
                            ? R.drawable.ic_item_heart_red : R.drawable.ic_item_heart_outline);
        }

        card.setCardBackgroundColor(
                ImageHelper.computeCardBackgroundColor(card.getContext(), photo.color));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            image.setTransitionName(photo.id + "-cover");
            card.setTransitionName(photo.id + "-background");
        }
    }

    void onRecycled() {
        ImageHelper.releaseImageView(image);
        likeButton.recycleImageView();
    }

    // interface.

    public interface ParentAdapter {
        void startPhotoActivity(View image, View background, int adapterPosition);
        void createCollectResult(Collection collection);
        void updateCollectResult(Photo photo, Collection collection, User user);
        void removePhotoFromCollectionResult(Photo photo, Collection collection, User user);
    }

    @OnClick(R.id.item_photo) void clickItem() {
        if (parentAdapter != null) {
            parentAdapter.startPhotoActivity(image, card, getAdapterPosition());
        }
    }

    @OnClick(R.id.item_photo_deleteButton) void deletePhoto() {
        MysplashActivity activity = Mysplash.getInstance().getTopActivity();
        if (activity instanceof CollectionActivity) {
            DeleteCollectionPhotoDialog dialog = new DeleteCollectionPhotoDialog();
            dialog.setDeleteInfo(((CollectionActivity) activity).getCollection(), photo);
            dialog.setOnDeleteCollectionListener(result -> {
                if (parentAdapter != null) {
                    parentAdapter.removePhotoFromCollectionResult(result.photo, result.collection, result.user);
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
        }
    }

    @OnClick(R.id.item_photo_avatar) void checkAuthor() {
        MysplashActivity activity = Mysplash.getInstance().getTopActivity();
        if (activity != null) {
            IntentHelper.startUserActivity(
                    activity, avatar, card, photo.user, UserActivity.PAGE_PHOTO);
        }
    }

    @OnClick(R.id.item_photo_likeButton) void likePhoto() {
        MysplashActivity activity = Mysplash.getInstance().getTopActivity();
        if (activity == null) {
            return;
        }
        if (AuthManager.getInstance().isAuthorized()) {
            if (likeButton.isUsable()) {
                likeButton.setProgressState();
                photo.settingLike = true;
                if (callback != null) {
                    callback.onLikeOrDislikePhoto(photo, getAdapterPosition(), !photo.liked_by_user);
                }
            }
        } else {
            IntentHelper.startLoginActivity(activity);
        }
    }

    @OnClick(R.id.item_photo_collectionButton) void collectPhoto() {
        MysplashActivity activity = Mysplash.getInstance().getTopActivity();
        if (activity != null) {
            if (!AuthManager.getInstance().isAuthorized()) {
                IntentHelper.startLoginActivity(activity);
            } else {
                SelectCollectionDialog dialog = new SelectCollectionDialog();
                dialog.setPhotoAndListener(photo, new OnCollectionsChangedListener(parentAdapter));
                dialog.show(activity.getSupportFragmentManager(), null);
            }
        }
    }

    @OnClick(R.id.item_photo_downloadButton) void downloadPhoto() {
        MysplashActivity activity = Mysplash.getInstance().getTopActivity();
        if (activity != null) {
            if (DatabaseHelper.getInstance(activity).readDownloadingEntityCount(photo.id) > 0) {
                NotificationHelper.showSnackbar(activity.getString(R.string.feedback_download_repeat));
            } else if (FileUtils.isPhotoExists(activity, photo.id)) {
                DownloadRepeatDialog dialog = new DownloadRepeatDialog();
                dialog.setDownloadKey(photo);
                dialog.setOnCheckOrDownloadListener(new OnCheckOrDownloadListener(photo));
                dialog.show(activity.getSupportFragmentManager(), null);
            } else if (callback != null) {
                callback.onDownload(photo);
            }
        }
    }

    // on collections changed listener.

    private class OnCollectionsChangedListener
            implements SelectCollectionDialog.OnCollectionsChangedListener {

        private ParentAdapter adapter;

        OnCollectionsChangedListener(ParentAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public void onAddCollection(Collection c) {
            adapter.createCollectResult(c);
        }

        @Override
        public void onUpdateCollection(Collection c, User u, Photo p) {
            adapter.updateCollectResult(p, c, u);
        }
    }

    // on check or download listener. (download repeat dialog)

    private class OnCheckOrDownloadListener implements DownloadRepeatDialog.OnCheckOrDownloadListener {

        private Photo photo;

        OnCheckOrDownloadListener(Photo photo) {
            this.photo = photo;
        }

        @Override
        public void onCheck(Object obj) {
            MysplashActivity activity = Mysplash.getInstance().getTopActivity();
            if (activity != null) {
                IntentHelper.startCheckPhotoActivity(activity, photo.id);
            }
        }

        @Override
        public void onDownload(Object obj) {
            if (callback != null) {
                callback.onDownload(photo);
            }
        }
    }
}