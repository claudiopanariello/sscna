/**********************************************************************
SuperScoreEvent
A single event (note or chord) for SScNA.

- midiNotes: single note or chord
- absTime: ONE onset per event (all notes of a chord start together)
- duration: a scalar
- velocity: a scalar in 1-127
- color:
- notehead, color: per event (whole chord)
- slots: free per-event data (lyrics, articulation, dynamics, ...)

**********************************************************************/

SScNAevent {
	var <midiNotes, <absTime, <duration, <velocity;
	var <>notehead, <>color;
	var <slots;

	*new { |midiNotes = 60, absTime = 0, duration = 1, velocity = 100|
		^super.new.init(midiNotes, absTime, duration, velocity);
	}

	init { |argMidiNotes, argAbsTime, argDuration, argVelocity|
		midiNotes = this.prSortNotes(argMidiNotes);
		absTime = argAbsTime;
		duration = argDuration;
		velocity = argVelocity;
		notehead = \noteShapeRoundBlack;
		color = nil; // nil = gray derived from velocity at render time
		slots = IdentityDictionary.new;
		this.prValidate;
	}

	absTime_ { |value|
		this.prCheckAbsTime(value);
		absTime = value;
	}


	midiNotes_ { |value|
		midiNotes = this.prSortNotes(value);
	}

	// negative values = enharmonic spelling: sort and play by absolute value
	pitches { ^midiNotes.abs }

	// first values validation, and then assignment
	duration_ { |value|
		this.prCheckDuration(value);
		duration = value;
	}

	// first values validation, and then assignment
	velocity_ { |value|
		this.prCheckVelocity(value);
		velocity = value;
	}

	// SLOTS
	slotAt { |key|
		^slots[key.asSymbol];
	}

	slotPut { |key, value|
		slots.put(key.asSymbol, value);
	}

	removeSlot { |key|
		^slots.removeAt(key.asSymbol);
	}

	slots_ { |dict|
		slots = IdentityDictionary.new;
		dict.keysValuesDo { |key, value| slots.put(key.asSymbol, value) };
	}

	// quickly access lyrics (if there is any) stored in slots
	lyrics { ^this.slotAt(\lyrics) }

	lyrics_ { |string| this.slotPut(\lyrics, string) }

	size { ^midiNotes.size }

	isChord { ^midiNotes.size > 1 }

	endTime { ^absTime + duration }

	// Event for playback. 'dur' (delta to the next event) is NOT set here:
	// it depends on the sequence, so it belongs to the container.
	asEvent {
		^(
			midinote: this.pitches,
			sustain: duration,
			amp: velocity / 127,
			absTime: absTime
		)
	}

	// deep copy, so arrays and slots are not shared between copies
	copy {
		^this.deepCopy;
	}

	printOn { |stream|
		stream << this.class.name
		<< "(midiNotes: " << midiNotes
		<< ", absTime: " << absTime
		<< ", duration: " << duration
		<< ", velocity: " << velocity
		<< ", notehead: " << notehead;
		if(color.notNil) { stream << ", color: " << color };
		if(slots.notEmpty) { stream << ", slots: " << slots };
		stream << ")";
	}

	storeOn { |stream|
		stream << this.class.name
		<< "(" <<< midiNotes << ", " <<< absTime
		<< ", " <<< duration << ", " <<< velocity << ")"
		<< ".notehead_(" <<< notehead << ")"
		<< ".color_(" <<< color << ")";
		if(slots.notEmpty) { stream << ".slots_(" <<< slots << ")" };
	}

	prSortNotes { |value|
		var arr = value.asArray.copy;
		this.prCheckMidiNotes(arr); // validate before .abs, so a bad input gives a clear error
		^arr.sort({ |a, b| a.abs < b.abs });
	}

	prCheckAbsTime { |value|
		if(value.isNumber.not or: { value < 0 }) {
			Error("SuperScoreEvent: absTime must be a number >= 0, got %".format(value)).throw;
		};
	}

	// duration 0 allowed on purpose (grace notes, markers)
	prCheckDuration { |value|
		if(value.isNumber.not or: { value < 0 }) {
			Error("SuperScoreEvent: duration must be a number >= 0, got %".format(value)).throw;
		}
	}

	prCheckMidiNotes { |value|
		if(value.isEmpty or: { value.every(_.isNumber).not }) {
			Error("SuperScoreEvent: midiNotes must be one or more numbers, got %".format(value)).throw;
		}
	}

	prCheckVelocity { |value|
		if(value.isNumber.not or: { value.inclusivelyBetween(0, 127).not }) {
			Error("SuperScoreEvent: velocity must be a number in 0..127, got %".format(value)).throw;
		};
	}

	prValidate {
		this.prCheckMidiNotes(midiNotes);
		this.prCheckAbsTime(absTime);
		this.prCheckDuration(duration);
		this.prCheckVelocity(velocity);
	}
}