package ch.amana.android.cputuner.view.adapter;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import ch.amana.android.cputuner.R;
import ch.amana.android.cputuner.helper.SettingsStorage;
import ch.amana.android.cputuner.hw.PowerProfiles;
import ch.amana.android.cputuner.log.Logger;
import ch.amana.android.cputuner.model.ModelAccess;
import ch.amana.android.cputuner.provider.db.DB;

public class ProfileAdaper extends BaseAdapter implements SpinnerAdapter {

	private final String AUTO;
	private final Cursor cursor;
	private final LayoutInflater layoutInflator;
	private final Context ctx;

	public ProfileAdaper(Context context, Cursor c) {
		super();
		this.ctx = context;
		this.cursor = c;
		this.layoutInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.AUTO = context.getString(R.string.auto);
	}

	@Override
	public int getCount() {
		if (cursor == null) {
			return 1;
		}
		return cursor.getCount() + 1;
	}

	@Override
	public Object getItem(int position) {
		if (position == 0) {
			if (SettingsStorage.getInstance().isEnableProfiles()) {
				return AUTO;
			} else {
				return ctx.getString(R.string.not_enabled);
			}
		} else if (position > 0) {
			return cursor.move(position - 1);
		} else {
			return ctx.getString(R.string.not_enabled);
		}
	}

	@Override
	public long getItemId(int position) {
		if (position == 0) {
			return PowerProfiles.AUTOMATIC_PROFILE;
		} else if (position == Integer.MAX_VALUE) {
			return position;
		}
		cursor.moveToPosition(position - 1);
		return cursor.getLong(DB.INDEX_ID);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView view = convertView != null ? (TextView) convertView : createView(parent);
		String text = "";
		if (position > 0) {
			try {
				if (cursor.moveToPosition(position - 1)) {
					text = cursor.getString(DB.CpuProfile.INDEX_PROFILE_NAME);
				}
			} catch (Throwable e) {
				Logger.i("Cannot get profilename from cursor", e);
			}
		} else {
			if (SettingsStorage.getInstance().isEnableProfiles()) {
				CharSequence profileName = ModelAccess.getInstace(parent.getContext()).getProfileName(PowerProfiles.getInstance().getCurrentAutoProfileId());
				text = AUTO;
				if (!PowerProfiles.UNKNOWN.equals(profileName)) {
					text = text + ": " + profileName;
				}
			} else {
				text = ctx.getString(R.string.not_enabled);
			}
		}
		view.setText(text);
		return view;
	}

	private TextView createView(ViewGroup parent) {
		TextView item;
		if (parent instanceof Spinner) {
			item = (TextView) layoutInflator.inflate(android.R.layout.simple_spinner_item, parent, false);
		} else {
			item = (TextView) layoutInflator.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
		}
		item.setSingleLine();
		item.setEllipsize(TextUtils.TruncateAt.END);
		return item;
	}

}
