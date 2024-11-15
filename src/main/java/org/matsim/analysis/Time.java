package org.matsim.analysis;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Time {

	private long hours;
	private long minutes;
	private long seconds;

	public Time(long totalSeconds) {
		this.hours = totalSeconds / 3600;
		totalSeconds %= 3600;
		this.minutes = totalSeconds / 60;
		this.seconds = totalSeconds % 60;
	}

	public Time(long hours, long minutes, long seconds) {
		this.hours = hours;
		this.minutes = minutes;
		this.seconds = seconds;
	}

	public long toSeconds() {
		return hours * 3600 + minutes * 60 + seconds;
	}

	@Override
	public String toString() {
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	public static Time fromString(String timeString) {
		String[] parts = timeString.split(":");
		long hours = Long.parseLong(parts[0]);
		long minutes = Long.parseLong(parts[1]);
		long seconds = Long.parseLong(parts[2]);
		return new Time(hours, minutes, seconds);
	}

	public long getHours() {
		return hours;
	}

	public long getMinutes() {
		return minutes;
	}

	public long getSeconds() {
		return seconds;
	}


}
