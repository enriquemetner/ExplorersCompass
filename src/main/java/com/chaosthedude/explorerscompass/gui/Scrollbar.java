package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

final class Scrollbar {
	static final int WIDTH = 6;
	static final int GRAB_PADDING = 2;
	private final int x;
	private final int top;
	private final int bottom;
	private final int thumbHeight;
	private final double offset;
	private final double maxOffset;

	Scrollbar(int x, int top, int bottom, int thumbHeight, double offset, double maxOffset) {
		this.x = x;
		this.top = top;
		this.bottom = bottom;
		this.thumbHeight = MathHelper.clamp(thumbHeight, 1, Math.max(1, bottom - top));
		this.maxOffset = Math.max(0, maxOffset);
		this.offset = MathHelper.clamp(offset, 0, this.maxOffset);
	}

	static int rowThumbHeight(int trackHeight, int visibleRows, int totalRows) {
		return Math.min(trackHeight, Math.max(12, trackHeight * visibleRows / Math.max(1, totalRows)));
	}

	static int pixelThumbHeight(int trackHeight, double maxOffset, int minimum, int bottomMargin) {
		int upper = Math.max(1, trackHeight - bottomMargin);
		return MathHelper.clamp((int) (trackHeight * trackHeight / (trackHeight + Math.max(0, maxOffset))), Math.min(minimum, upper), upper);
	}

	static double scrollPixels(double offset, double wheelAmount, int rowHeight, double maxOffset) {
		double target = offset - wheelAmount * rowHeight;
		if (wheelAmount != 0 && Math.rint(wheelAmount) == wheelAmount) {
			double row = offset / rowHeight - wheelAmount;
			target = (wheelAmount < 0 ? Math.floor(row) : Math.ceil(row)) * rowHeight;
		}
		return MathHelper.clamp(target, 0, maxOffset);
	}

	static int scrollRows(int offset, double wheelAmount, int rowsPerTick, int maxOffset) {
		return MathHelper.clamp(offset - (int) Math.signum(wheelAmount) * rowsPerTick, 0, maxOffset);
	}

	boolean isVisible() {
		return maxOffset > 0;
	}

	boolean contains(double mouseX, double mouseY) {
		return isVisible() && mouseX >= x && mouseX < x + WIDTH && mouseY >= top && mouseY < bottom;
	}

	int thumbTop() {
		return top + (int) (offset * (bottom - top - thumbHeight) / maxOffset);
	}

	boolean outsideThumb(double mouseY, int padding) {
		return mouseY < thumbTop() - padding || mouseY >= thumbTop() + thumbHeight + padding;
	}

	double grabOffset(double mouseY, int padding) {
		return outsideThumb(mouseY, padding) ? thumbHeight / 2.0 : MathHelper.clamp(mouseY - thumbTop(), 0, thumbHeight);
	}

	double offsetForMouse(double mouseY, double grabOffset) {
		int travel = Math.max(1, bottom - top - thumbHeight);
		return MathHelper.clamp((mouseY - top - grabOffset) * maxOffset / travel, 0, maxOffset);
	}

	void render(DrawContext context) {
		if (!isVisible()) return;
		context.fill(x, top, x + WIDTH, bottom, RenderUtils.getBackgroundColor(false, false));
		RenderUtils.drawScrollbarThumb(context, x, thumbTop(), WIDTH, thumbHeight, RenderUtils.getBackgroundColor(true, true));
	}
}
