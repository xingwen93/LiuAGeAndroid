package tv.baokan.liuageandroid.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;

import tv.baokan.liuageandroid.R;
import tv.baokan.liuageandroid.adapter.DragGridViewAdapter;
import tv.baokan.liuageandroid.adapter.OptionalGridViewAdapter;
import tv.baokan.liuageandroid.model.ColumnBean;
import tv.baokan.liuageandroid.utils.LogUtils;
import tv.baokan.liuageandroid.utils.StreamUtils;
import tv.baokan.liuageandroid.widget.DragGridView;
import tv.baokan.liuageandroid.widget.NavigationViewPush;
import tv.baokan.liuageandroid.widget.OptionalGridView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ColumnActivity extends BaseActivity implements OnItemClickListener {

    private static final String TAG = "ColumnActivity";

    private OptionalGridView mOtherGv;
    private DragGridView mUserGv;
    private List<ColumnBean> selectedList = new ArrayList<>();
    private List<ColumnBean> optionalList = new ArrayList<>();
    private OptionalGridViewAdapter mOptionalGridViewAdapter;
    private DragGridViewAdapter mDragGridViewAdapter;
    private NavigationViewPush mNavigationViewRed;

    /**
     * 便捷启动当前activity
     *
     * @param activity 启动当前activity的activity
     */
    public static void start(Activity activity, List<ColumnBean> selectedList, List<ColumnBean> optionalList) {
        Intent intent = new Intent(activity, ColumnActivity.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable("selectedList_key", (Serializable) selectedList);
        bundle.putSerializable("optionalList_key", (Serializable) optionalList);
        intent.putExtras(bundle);
        activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_COLUMN);
        activity.overridePendingTransition(R.anim.column_show, R.anim.column_bottom);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_column);

        // 取出栏目数据
        Intent intent = getIntent();
        selectedList = (List<ColumnBean>) intent.getSerializableExtra("selectedList_key");
        optionalList = (List<ColumnBean>) intent.getSerializableExtra("optionalList_key");

        mNavigationViewRed = (NavigationViewPush) findViewById(R.id.nav_column);
        mNavigationViewRed.getRightView().setImageResource(R.drawable.top_navigation_close_dark);
        mNavigationViewRed.setupNavigationView(false, true, "栏目管理", new NavigationViewPush.OnClickListener() {
            @Override
            public void onRightClick(View v) {
                setupResultData();
                finish();
            }
        });

        // 准备UI
        prepareUI();
    }

    /**
     * 准备UI
     */
    public void prepareUI() {
        mUserGv = (DragGridView) findViewById(R.id.userGridView);
        mOtherGv = (OptionalGridView) findViewById(R.id.otherGridView);

        // 配置GridView
        mDragGridViewAdapter = new DragGridViewAdapter(this, selectedList, true);
        mOptionalGridViewAdapter = new OptionalGridViewAdapter(this, optionalList, false);
        mUserGv.setAdapter(mDragGridViewAdapter);
        mOtherGv.setAdapter(mOptionalGridViewAdapter);
        mUserGv.setOnItemClickListener(this);
        mOtherGv.setOnItemClickListener(this);
    }

    // 返回true则是不继续传播事件，自己处理。返回false则系统继续传播处理
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setupResultData();
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 设置回调数据
     */
    private void setupResultData() {
        // 在回调数据之前，缓存数据到本地
        HashMap<String, List<ColumnBean>> map = new HashMap<>();
        map.put("selected", mDragGridViewAdapter.getSelectedList());
        map.put("optional", mOptionalGridViewAdapter.getOptionalList());
        String jsonString = JSON.toJSONString(map);
        // 将栏目数据写入本地
        StreamUtils.writeStringToFile("column.json", jsonString);
        LogUtils.d(TAG, jsonString);

        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putSerializable("selectedList_key", (Serializable) mDragGridViewAdapter.getSelectedList());
        bundle.putSerializable("optionalList_key", (Serializable) mOptionalGridViewAdapter.getOptionalList());
        intent.putExtras(bundle);
        // 这里会回调给MainActivity，然后在MainActivity里传递给NewsFragment
        if (mDragGridViewAdapter.isListChanged) {
            // 改变过才需要刷新
            setResult(RESULT_OK, intent);
        }

    }

    /**
     * 获取点击的item的对应View，
     * 因为点击的Item已经有了自己归属的父容器MyGridView，所有我们要是有一个ImageView来代替Item移动
     *
     * @param view
     * @return
     */
    private ImageView getView(View view) {
        view.destroyDrawingCache();
        view.setDrawingCacheEnabled(true);
        Bitmap cache = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(cache);
        return iv;
    }

    /**
     * 获取移动的view，放入对应ViewGroup布局容器
     *
     * @param viewGroup
     * @param view
     * @param initLocation
     * @return
     */
    private View getMoveView(ViewGroup viewGroup, View view, int[] initLocation) {
        int x = initLocation[0];
        int y = initLocation[1];
        viewGroup.addView(view);
        LinearLayout.LayoutParams mLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLayoutParams.leftMargin = x;
        mLayoutParams.topMargin = y;
        view.setLayoutParams(mLayoutParams);
        return view;
    }

    /**
     * 创建移动的item对应的ViewGroup布局容器
     * 用于存放我们移动的View
     */
    private ViewGroup getMoveViewGroup() {
        // window中最顶层的view
        ViewGroup moveViewGroup = (ViewGroup) getWindow().getDecorView();
        LinearLayout moveLinearLayout = new LinearLayout(this);
        moveLinearLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        moveViewGroup.addView(moveLinearLayout);
        return moveLinearLayout;
    }

    /**
     * 点击item移动动画
     *
     * @param moveView
     * @param startLocation
     * @param endLocation
     * @param moveColumn
     * @param clickGridView
     */
    private void moveAnimation(View moveView, int[] startLocation, int[] endLocation, final ColumnBean moveColumn, final GridView clickGridView, final boolean isSelectedItem) {
        int[] initLocation = new int[2];
        // 获取传递过来的VIEW的坐标
        moveView.getLocationInWindow(initLocation);
        // 得到要移动的VIEW,并放入对应的容器中
        final ViewGroup moveViewGroup = getMoveViewGroup();
        final View mMoveView = getMoveView(moveViewGroup, moveView, initLocation);
        // 创建移动动画
        TranslateAnimation moveAnimation = new TranslateAnimation(
                startLocation[0],
                endLocation[0],
                startLocation[1],
                endLocation[1]);
        moveAnimation.setDuration(300L);
        // 动画配置
        AnimationSet moveAnimationSet = new AnimationSet(true);
        // 动画效果执行完毕后，View对象不保留在终止的位置
        moveAnimationSet.setFillAfter(false);
        moveAnimationSet.addAnimation(moveAnimation);
        mMoveView.startAnimation(moveAnimationSet);
        moveAnimationSet.setAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                moveViewGroup.removeView(mMoveView);
                // 判断点击的是SelectedDragGrid还是OptionalGridView
                if (isSelectedItem) {
                    mOptionalGridViewAdapter.setVisible(true);
                    mOptionalGridViewAdapter.notifyDataSetChanged();
                    mDragGridViewAdapter.remove();
                } else {
                    mDragGridViewAdapter.setVisible(true);
                    mDragGridViewAdapter.notifyDataSetChanged();
                    mOptionalGridViewAdapter.remove();
                }
            }
        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
        switch (parent.getId()) {
            case R.id.userGridView:
                // position为 0 的不可以进行任何操作
                if (position != 0) {
                    final ImageView moveImageView = getView(view);
                    if (moveImageView != null) {
                        TextView newTextView = (TextView) view.findViewById(R.id.text_item);
                        final int[] startLocation = new int[2];
                        newTextView.getLocationInWindow(startLocation);
                        final ColumnBean columnBean = ((DragGridViewAdapter) parent.getAdapter()).getItem(position);//获取点击的频道内容
                        mOptionalGridViewAdapter.setVisible(false);
                        // 添加到最后一个
                        mOptionalGridViewAdapter.addItem(columnBean);
                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                try {
                                    int[] endLocation = new int[2];
                                    // 获取终点的坐标
                                    mOtherGv.getChildAt(mOtherGv.getLastVisiblePosition()).getLocationInWindow(endLocation);
                                    moveAnimation(moveImageView, startLocation, endLocation, columnBean, mUserGv, true);
                                    mDragGridViewAdapter.setRemove(position);
                                } catch (Exception localException) {
                                    localException.printStackTrace();
                                }
                            }
                        }, 50L);
                    }
                }
                break;
            case R.id.otherGridView:
                final ImageView moveImageView = getView(view);
                if (moveImageView != null) {
                    TextView newTextView = (TextView) view.findViewById(R.id.text_item);
                    final int[] startLocation = new int[2];
                    newTextView.getLocationInWindow(startLocation);
                    final ColumnBean columnBean = ((OptionalGridViewAdapter) parent.getAdapter()).getItem(position);
                    mDragGridViewAdapter.setVisible(false);
                    // 添加到最后一个
                    mDragGridViewAdapter.addItem(columnBean);
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            try {
                                int[] endLocation = new int[2];
                                // 获取终点的坐标
                                mUserGv.getChildAt(mUserGv.getLastVisiblePosition()).getLocationInWindow(endLocation);
                                moveAnimation(moveImageView, startLocation, endLocation, columnBean, mOtherGv, false);
                                mOptionalGridViewAdapter.setRemove(position);
                            } catch (Exception localException) {
                                localException.printStackTrace();
                            }
                        }
                    }, 50L);
                }
                break;
            default:
                break;
        }
    }

}
