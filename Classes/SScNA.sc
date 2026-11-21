/**********************************************************************

...d888888o.      d888888o.       ,o888888o.    b.             8          .8.
..`8888:' `88.  .`8888:' `88.    8888     `88.  888o.          8         .888.
.8.`8888.   Y8  8.`8888.   Y8 ,8 8888       `8. Y88888o.       8        :88888.
.`8.`8888.      `8.`8888.     88 8888           .`Y888888o.    8       . `88888.
..`8.`8888.      `8.`8888.    88 8888           8o. `Y888888o. 8      .8. `88888.
...`8.`8888.      `8.`8888.   88 8888           8`Y8o. `Y88888o8     .8`8. `88888.
....`8.`8888.      `8.`8888.  88 8888           8   `Y8o. `Y8888    .8' `8. `88888.
8b   `8.`8888. 8b   `8.`8888. `8 8888       .8' 8      `Y8o. `Y8   .8'   `8. `88888.
`8b.  ;8.`8888 `8b.  ;8.`8888    8888     ,88'  8         `Y8o.`  .888888888. `88888.
.`Y8888P ,88P'  `Y8888P ,88P'     `8888888P'    8            `Yo .8'       `8. `88888.


SupercolliderSCoreNotationAssistant (SScNA)
Displaying proportional notation natively in SC (something similar to a maxmsp bach.roll)

Class started on 2026-03 (but first code done in 2025)
Claudio Panariello
Email: 	claudio@claudiopanariello.com
URL:	https://claudiopanariello.com/


TO DO (not necessarily in this order eheh):
- Avoid collisions in chords
- Select a note from the GUI
- Check the conversion velocities to amp (now it's .linlin)
- Add bending to .eazyMIDIPlay
- Fix the way midiNotes is interpreted: for now, when it starts with a chord, it needs some extra brackets in order to get properly recognized
- Automatically stop the playbackline when reaches the end of the roll

**********************************************************************/

SScNA {
	var <>midiNotes, <>rhythms, <>velocities, <>clefs, <>windowTitle, <>scale, <>defaultSpacing, <>forceAccidentals, <>showDurations, <>showStems, <>accidentalMode, <staffNames;
	var <>windowWidth = 500, <>windowHeight = 500, <>posX = 600, <>posY = 300, <>parent = nil;
	var <>window, <>userView;

	var musicFont, offsetY;

	var <>durations;
	var <>staffPosition;
	var <>staffWidth, <> staffHeight;
	var <staves;
	var <>toneDivision, majorScale, diatonicSteps;
	var colorVelocity;

	var prevNote, prevNotePosX, prevNotePosY, prevNotePosYchord;
	var numStaves;
	var tempoBpm = 60;
	var <>playbackPoint = 0, pixelsPerSecond = 0, startTimePlayback = 0, playbackOffset = 0;
	var <>scrollView;
	var isPaused = false, pauseTime = 0, players;
	var isPlaying = false;
	var <>midiOut;

	var staffGap; // vertical gap between the bottom line of a staff and the top line of the next one
	var spaceInterline;

	*new { |midiNotes = 60, rhythms = nil, velocities = 100, clefs = \g, windowTitle = "Music Roll", scale = 1, defaultSpacing = 100, forceAccidentals = false, showDurations = true, showStems = true, accidentalMode = \nearest, staffNames|
		^super.newCopyArgs(midiNotes.asArray, rhythms, velocities, clefs, windowTitle, scale, defaultSpacing, forceAccidentals, showDurations, showStems, accidentalMode, staffNames).init;
	}

	init {
		staffPosition = 150; // gap between top of the window and first staff
		spaceInterline = 10;
		staffGap = 14*spaceInterline; // gap between staves
		toneDivision = 16; // 96TET
		majorScale = Scale.major.degrees*(toneDivision div:2); // integer division
		diatonicSteps = majorScale ++ [12 * (toneDivision div: 2)]; // one full octave

		musicFont = "Bravura";
		offsetY = 76; //<----------- IMPORTANT! For Bravura offsetY = 76; for Petaluma offsetY = 38

		MIDIClient.initialized.not.if { MIDIClient.init };
		MIDIIn.connectAll;

		if(Font.availableFonts.includesEqual("Bravura") == false, {
			"Bravura font is missing. Please install from https://github.com/steinbergmedia/bravura and restart SC".throw;
		});

		if(Font.availableFonts.includesEqual("Petaluma") == false, {
			"Petaluma font is missing. Please install from https://github.com/steinbergmedia/petaluma and restart SC".throw;
		});

		if([\nearest, \allSharp].includes(accidentalMode).not) {
			^"accidentalMode must be \\nearest or \\allSharp".warn;
		};

		midiNotes = midiNotes.fixShape;
		numStaves = midiNotes.shape[0];
		format("There are % staves in total.", numStaves).postln;
		this.fixDurations;
		this.fixVelocities;

		//fix clefs: I need an array of numStaves elements, all strings
		clefs = clefs.asArray;
		if(clefs.size<numStaves, {clefs = clefs ++ (clefs.last!(numStaves-clefs.size))});
		clefs = clefs.collect({|i| i.asString});

		this.prFixStaffNames;
		this.buildModel; // building staves and events

		this.updateCanvasSize; // this should now work better in displaying the correct window length

		SScNAglyphs.load(Platform.userExtensionDir +/+ "cpClasses/SScNA/SMuFL_fonts_json/glyphnames.json");

		this.buildGui;
	}

	fixVelocities {
		if(velocities.isNil or: {velocities.size==0}, {
			var auxValue = velocities ?? 100;
			velocities = List.new;
			numStaves.do({|n|
				velocities.add(midiNotes[n].collect({ auxValue }));
			});
			velocities = velocities.asArray;
		});
		//velocities = velocities.clipExtend(midiNotes[n].size);
		velocities = velocities.fixShape;
	}

	fixDurations {
		//brutal way to create a rhythms array identical to midiNotes but filled with 1s (or the given value, only if it's ONE value)
		if(rhythms.isNil or: {rhythms.size==0}, {
			var auxValue = rhythms ?? 1;
			rhythms = List.new;
			numStaves.do({|n|
				rhythms.add(midiNotes[n].collect({ auxValue }));
			});
			rhythms = rhythms.asArray;
			//rhythms.postln;
		});

		rhythms = rhythms.fixShape;
		durations = List.new;
		numStaves.do({|n|
			var onsets, isRest, triplets;
			onsets  = ([0] ++ rhythms[n].abs.integrate).drop(-1);
			isRest  = rhythms[n].collect { |d| d < 0 };
			triplets = [onsets, isRest, rhythms[n].abs].flop;
			durations.add(triplets); //triplets onsets-isRest-actualDuration
		});
	}

	buildGui {
		this.createWindow(windowTitle);
		userView = UserView.new(scrollView, staffWidth@staffHeight)
		.background_(Color.gray(1));
		userView.resize = 5;
		userView.drawFunc = { Pen.scale(scale, scale); this.renderMusic };
		CmdPeriod.add(this);
	}

	// staves and events, always rebuilt together
	buildModel {
		this.buildStaves;
		this.buildEvents;
	}

	// stack staves: each one starts staffGap below the previous one's lowest line.
	// staffPosition is the y of the topmost line of the whole score
	buildStaves {
		var nextTop = staffPosition;
		staves = clefs.collect({ |c, n|
			var staff = SScNAstaff(c, staffNames[n], nextTop, spaceInterline);
			staff.top_(staff.top + (nextTop - staff.topY)); // align its highest line to nextTop
			nextTop = staff.bottomY + staffGap;
			staff
		});
	}

	// SScNA receives arrays of rhythms, but SScNAevent wants absTime and duration
	// therefore: temporary adapter: old arrays -> events. durations[n] items are [onset, isRest, dur]
	// gotta FIX this in the future
	buildEvents {
		staves.do({ |staff, n|
			var notes = durations[n].reject({ |item| item[1] });
			if(notes.size != midiNotes[n].size) {
				"SScNA: staff % has % notes but % non-rest rhythms".format(n, midiNotes[n].size, notes.size).warn;
			};
			min(notes.size, midiNotes[n].size).do({ |idx|
				staff.addEvent(SScNAevent(midiNotes[n][idx], notes[idx][0], notes[idx][2], velocities[n][idx]));
			});
		});
	}

	createWindow { |title|
		window = Window.new(
			title,
			Rect(posX, posY, windowWidth, windowHeight),
			resizable: true,
		).alwaysOnTop_(true).front;

		scrollView = ScrollView.new(window, Rect(0, 0, windowWidth, windowHeight))
		.resize_(5)
		.autohidesScrollers_(true)
		.hasVerticalScroller_(false);

		window.onClose_({
			CmdPeriod.remove(this);
			this.stop;
		});
	}

	renderMusic { |showPlayback = true|
		staves.do({ |staff|
			staff.draw(staffWidth / scale, Font(musicFont, 38), offsetY);
			prevNote = nil;
			prevNotePosY = nil;
			staff.events.do({ |event|
				var posX;
				prevNotePosYchord = nil;
				posX = 80 + (event.absTime * defaultSpacing);
				event.midiNotes.do({ |note| this.addNote(note, posX, staff, event) });
			});
		});
		//this.playbackLine(staves.last.bottomY); // print playback line
		if(showPlayback, { this.playbackLine(staves.last.bottomY) }); //show playbackLine only on SC, not for printing
	}

	addNote {|inputNote = 66, posX, staff, event|
		var pitch            = inputNote.abs;
		var currentDuration  = event.duration * defaultSpacing;
		var stepsPerSemitone = toneDivision div: 2;
		var stepsPerOctave   = 12 * stepsPerSemitone;
		var absStep          = (pitch * stepsPerSemitone).round.asInteger;
		var octave           = (absStep div: stepsPerOctave) - 1;
		var step96           = absStep % stepsPerOctave;
		var nearestDiatonicStep, deviation, posY, index;

		if(accidentalMode == \nearest){
			nearestDiatonicStep = step96.nearestInList(diatonicSteps) }
		{ nearestDiatonicStep = diatonicSteps[diatonicSteps.indexInBetween(step96).floor] };
		deviation = step96 - nearestDiatonicStep;

		// if inputNote is negative then it picks the enharmonic
		if(inputNote < 0 ) {
			index = diatonicSteps.indexOf(nearestDiatonicStep) + deviation.sign;
			nearestDiatonicStep = diatonicSteps[index];
			deviation = step96 - nearestDiatonicStep;
		};

		posY = staff.yForStep(diatonicSteps.indexOf(nearestDiatonicStep), octave);

		// velocity mapped to gray scale
		colorVelocity = event.color ?? { Color.gray(event.velocity.linlin(1, 127, 0.7, 0.0)) };
		// this affects noteheads, accidental, stems, ledger lines, and duration line
		if(showDurations) { this.addDurationLine(posX, posY, currentDuration) };

		Pen.stringAtPoint(
			SScNAglyphs.at(event.notehead),
			(posX-7)@(posY-offsetY),
			Font(musicFont, 38),
			colorVelocity);

		staff.drawLedgerLines(pitch, posX, posY, colorVelocity);

		this.addAccidental(deviation, posX, posY, prevNotePosY);
		if(showStems) { this.addStem(posX, posY, prevNotePosYchord) };

		// check and resolve collisions: TO BE DONE
		/*
		if(prevPosX.isNil, {prevPosX = posX});
		if(prevPosX==posX and:(inputNote-prevNote)<4, {
		avoidCollision = posY

		}
		);
		*/

		prevNote = pitch; // abs pitch!
		prevNotePosX = posX;
		prevNotePosY = posY;
		prevNotePosYchord = posY;
	}

	addAccidental {|deviation, posX, posY, prevPosY|
		if(forceAccidentals == false, {
			if(prevPosY != posY and: {deviation==0}, {
				deviation = nil})
		});
		Pen.stringAtPoint(SScNAglyphs.accidental(deviation), (posX-21)@(posY-offsetY), Font(musicFont, 38), colorVelocity;);
	}

	addStem {|posX, posY, prevPosY|
		if(prevPosY.isNil, {prevPosY = posY});
		Pen.strokeColor_(colorVelocity);
		Pen.width_(1);
		Pen.line(
			Point(posX-7, posY),
			Point(posX-7, prevPosY+34);
		);
		Pen.stroke;
	}

	addDurationLine {|posX, posY, dur|
		Pen.strokeColor_(colorVelocity);
		Pen.width_(4);
		Pen.line(
			Point(posX, posY),
			Point(posX-7+dur, posY);
		);
		Pen.stroke;
	}

	updateCanvasSize {
		var maxBeats = staves.collect({ |s| s.endTime }).maxItem;
		var noteOriginX = 80; // same origin used in renderMusic
		var rightMargin = 40;
		var bottomMargin = 100; // room for ledger lines below the last staff

		staffWidth = windowWidth.max(scale * (noteOriginX + (maxBeats * defaultSpacing) + rightMargin));
		staffHeight = windowHeight.max(scale * (staves.last.bottomY + bottomMargin));

		if(userView.notNil, {
			userView.bounds_(Rect(0, 0, staffWidth, staffHeight));
		});
	}

	//draw playback line
	playbackLine { |bottomY|
		var elapsed;
		if(isPlaying and: { isPaused.not }, {
			elapsed = thisThread.seconds - startTimePlayback;
			playbackPoint = playbackOffset + (elapsed * pixelsPerSecond);
			scrollView.visibleOrigin_(Point((playbackPoint - (windowWidth / 2) * scale).max(0), 0));
		}, {
			playbackPoint = playbackOffset;
		});

		Pen.strokeColor_(Color.new255(0, 200, 50));
		Pen.width_(2);
		Pen.line(
			Point(60+playbackPoint, staffPosition-100+bottomY),
			Point(60+playbackPoint, staffPosition-70);
		);
		Pen.line(
			Point(60+playbackPoint, staffPosition-70),
			Point(50+playbackPoint, staffPosition-80);
		);
		Pen.line(
			Point(60+playbackPoint, staffPosition-70),
			Point(70+playbackPoint, staffPosition-80);
		);
		Pen.stroke;
	}

	setBpm {|bpm = 60|
		playbackOffset = playbackOffset + ((thisThread.seconds - startTimePlayback) * pixelsPerSecond);
		startTimePlayback = thisThread.seconds;
		tempoBpm = bpm;
		TempoClock.default.tempo = (tempoBpm/60); //beats/seconds, so 2 = 120 BPM).
		pixelsPerSecond = defaultSpacing * (bpm / 60);
	}



	eazyPlay { |bpm = 60, instrument = \default, startBeat = 0, startSeconds|
		startBeat = this.prResolveStartBeat(bpm, startBeat, startSeconds);
		if(this.prCheckStartBeat(startBeat).not) { ^this };
		this.prResetPlayback(bpm, startBeat);

		Server.local.waitForBoot {
			startTimePlayback = thisThread.seconds; // playbackOffset resta = startBeat
			userView.animate_(true).frameRate_(30); // always 30, never changes
			players = numStaves.collect({ |n|
				var data = this.prPlaybackDataFrom(n, startBeat);
				Pbind(
					\instrument, instrument,
					\dur,        Pseq(data[0], 1),
					\midinote,   Pseq(data[1], 1),
					\amp,        Pseq(data[2] / numStaves, 1),
					\legato,     0.8
				).play;
			});
		}
	}

	eazyMIDIplay { |bpm = 60, midiPort = 0, startBeat = 0, startSeconds|
		startBeat = this.prResolveStartBeat(bpm, startBeat, startSeconds);
		if(this.prCheckStartBeat(startBeat).not) { ^this };
		midiOut = MIDIOut(midiPort);
		this.prResetPlayback(bpm, startBeat);

		userView.animate_(true).frameRate_(30);
		players = numStaves.collect({ |n|
			var data = this.prPlaybackDataFrom(n, startBeat);
			Pbind(
				\type,     \midi,
				\midiout,  midiOut,
				\dur,      Pseq(data[0], 1),
				\midinote, Pseq(data[1], 1),
				\amp,      Pseq(data[2] / numStaves, 1),
				\sustain,  Pseq(data[0], 1)
			).play;
		});
	}

	pause {
		if(isPaused.not) {
			players.do({ |p| p.pause });
			pauseTime = thisThread.seconds;
			// save position at pause moment
			playbackOffset = playbackOffset + ((pauseTime - startTimePlayback) * pixelsPerSecond);
			userView.animate_(false);
			isPaused = true;
		}
	}

	resume {
		if(isPaused) {
			players.do({ |p| p.resume });
			startTimePlayback = thisThread.seconds;
			userView.animate_(true);
			isPaused = false;
		}
	}

	stop {
		players.do({ |p| p.stop });
		userView.animate_(false);
		isPlaying = false;
		isPaused = false;
		playbackPoint = 0;
		playbackOffset = 0;
		scrollView.visibleOrigin_(0@0);
		userView.refresh;
	}

	// quick method to access the events of a staff
	eventsAt { |staffIndex|
		^staves[staffIndex].events;
	}

	setFont {|font = "Bravura"|
		musicFont = font;
		if(musicFont== "Bravura", {offsetY = 76});
		if(musicFont== "Petaluma", {offsetY = 38});
		userView.refresh;
	}

	setDefaultSpacing { |value|
		defaultSpacing = value;
		this.updateCanvasSize;
		userView.refresh;
	}

	// update with a new clef
	changeClefs { |newClefs|
		clefs = newClefs.asArray;
		if(clefs.size<numStaves, {clefs = clefs ++ (clefs.last!(numStaves-clefs.size))});
		clefs = clefs.collect({|i| i.asString});
		this.buildModel;
		this.updateCanvasSize;
		userView.refresh;
	}

	// force show all accidentals
	setForceAccidentals {|boolean|
		forceAccidentals = boolean;
		userView.refresh;
	}

	// choose between the two accidentals mode
	setAccidentalMode { |mode = \nearest|
		if([\nearest, \allSharp].includes(mode).not) {
			"accidentalMode must be \\nearest or \\allSharp".warn;
		};
		accidentalMode = mode;
		userView.refresh;
	}

	// set the staves names
	setStaffNames { |names|
		staffNames = names;
		this.prFixStaffNames;
		staves.do({ |staff, n| staff.name_(staffNames[n]) });
		userView.refresh;
	}

	// set the view scale
	setScale { |value|
		scale = value;
		this.updateCanvasSize;
		userView.refresh;
	}

	// show the duration lines
	setShowDurations {|boolean|
		showDurations = boolean;
		userView.refresh;
	}

	// show or not the stems
	setShowStems {|boolean|
		showStems = boolean;
		userView.refresh;
	}

	// get all the midinotes as array
	getPitches {
		^midiNotes;
	}

	// get all the rhythms as array
	getRhythms {
		^rhythms;
	}

	// get all the velocities as array
	getVelocities {
		^velocities;
	}

	exportToOM {|fileName|
		fileName = fileName ? windowTitle;
		if (SuperOM.isNil) {
			"SuperOM is missing. Please install from https://github.com/claudiopanariello/SuperOM".throw;
		};

		SuperOM.new.writeOMfile(
			fileName: fileName++".omi",
			midicents: this.getPitches.abs*100, //default SuperOM is in midicents, while SScNA is in midi
			rhythmTree: this.getRhythms
		)
	}

	exportToLilyPond {|fileName|
		fileName = fileName ? windowTitle;
		if (Fosc.isNil) {
			"Fosc is missing. Please install from https://github.com/prko/fosc/tree/master".throw;
		};
		"Still working on it... :)".warn;
	}

	// Export the score as a vector PDF via the system print dialog
	// (choose "Save as PDF" / "Print to file" as destination).
	// printScale: nil = auto-fit the score height to the page; otherwise a fixed factor.
	// Long scores are split horizontally across pages.
	exportToPDF { |printScale|
		var printer = QPenPrinter.new;
		var contentW = staffWidth / scale; // back to unscaled score units
		var contentH = staffHeight / scale;

		printer.showDialog({
			printer.print({
				var page = printer.pageRect;
				var pageScale = printScale ?? { (page.height / contentH).min(1) };
				var systemW = page.width / pageScale; // score units that fit on one page
				var numPages = (contentW / systemW).ceil.asInteger.max(1);

				numPages.do({ |i|
					var originX = i * systemW;
					if(i > 0, { printer.newPage });
					Pen.push;
					Pen.scale(pageScale, pageScale);
					Pen.translate(originX.neg, 0);
					Pen.addRect(Rect(originX, 0, systemW, contentH));
					Pen.clip;
					this.renderMusic(false);
					Pen.pop;
				});
				format("SScNA: exported % page(s).", numPages).postln;
			});
		}, {
			"SScNA: PDF export cancelled.".postln;
		});
	}

	// update with new music
	// since a lot of preprocessing is done in .init and not in .renderMusic, I have to re-run a bunch of lines
	// Not sure this is the best way to do it, but anyway, it works for now
	updateMusic { |newPitches, newRhythms, newVelocities, newClefs|
		midiNotes = newPitches ?? { midiNotes };
		rhythms   = newRhythms ?? { rhythms };
		velocities   = newVelocities ?? { velocities };
		clefs     = (newClefs ?? { clefs }).asArray;

		midiNotes = midiNotes.asArray.fixShape;
		numStaves = midiNotes.shape[0];

		if(clefs.size<numStaves, {clefs = clefs ++ (clefs.last!(numStaves-clefs.size))});
		clefs = clefs.collect({|i| i.asString});

		this.fixDurations;
		this.fixVelocities;
		this.buildModel;
		this.updateCanvasSize;

		scrollView.visibleOrigin_(0@0); //for safety, to bring the scrollView back to the origin
		userView.refresh;
	}

	// staffNames: nil, a single name, or an array (missing entries give no name)
	prFixStaffNames {
		staffNames = if(staffNames.isString) { [staffNames] } { staffNames.asArray };
	}
	// it gives back [durs, pitches, amps] for each staff, starting from startBeat
	prPlaybackDataFrom { |n, startBeat = 0|
		var durs = List.new, pitches = List.new, amps = List.new;
		var onset = 0, position = 0;
		var eps = 1e-6; // tolerance for floating points error

		rhythms[n].do({ |d|
			var dur = d.abs;
			var isRest = d < 0;
			var end = onset + dur;
			var pitch, amp;

			if(isRest.not) {
				pitch = midiNotes[n][position];
				amp = if(pitch.isArray)
				{ velocities[n][position] / pitch.size / 127 }
				{ velocities[n][position] / 127 };
				position = position + 1;
			};

			case
			{ end <= (startBeat + eps) } { }
			{ onset < (startBeat - eps) } {
				durs.add(Rest(end - startBeat));
				pitches.add(Rest());
				amps.add(0);
			}
			{
				durs.add(if(isRest) { Rest(dur) } { dur });
				pitches.add(if(isRest) { Rest() } { pitch.abs });
				amps.add(if(isRest) { 0 } { amp });
			};

			onset = end;
		});
		^[durs.asArray, pitches.asArray, amps.asArray]
	}

	prResetPlayback { |bpm, startBeat|
		players.do({ |p| p.stop }); // don't call new players if eazyPlay already playing
		isPaused = false;
		isPlaying = true;
		startTimePlayback = thisThread.seconds;
		this.setBpm(bpm);
		playbackOffset = startBeat * defaultSpacing;
		playbackPoint = playbackOffset;
		scrollView.visibleOrigin_(Point((playbackPoint - (windowWidth / 2) * scale).max(0), 0));
	}

	prCheckStartBeat { |startBeat|
		var maxBeats = staves.collect({ |s| s.endTime }).maxItem;
		if(startBeat >= maxBeats) {
			"SScNA: startBeat (%) is beyond the end of the score (% beats)".format(startBeat, maxBeats).warn;
			^false
		};
		^true
	}

	// convrert startSeconds in beats
	// startSeconds has higher priority on startBeat (i.e. if both are given, it uses startSeconds)
	prResolveStartBeat { |bpm, startBeat, startSeconds|
		var beat = startBeat ? 0;
		if(startSeconds.notNil) {
			if(startBeat.notNil and: { startBeat != 0 }) {
				"SScNA: both startBeat and startSeconds given, using startSeconds".warn;
			};
			beat = startSeconds * (bpm / 60);
		};
		^beat.max(0)
	}

	refresh {
		userView.refresh; // Redraw
	}

	cmdPeriod {
		this.stop;
	}

	show {
		if(window.isNil or: { window.isClosed }, {
			this.buildGui;
		}, {
			window.front;
		});
	}

	free {
		window.close;
	}

	*bachInvention {
		var bach_RH_P, bach_RH_R, bach_LH_R, bach_LH_P;
		// pitches right hand Bach Invention n.1
		bach_RH_P = [60, 62, 64, 65, 62, 64, 60, 67, 72, 71, 69, 71, 72, 74, 67, 69, 71, 72, 69, 71, 67, 74, 79, 77, 76, 77, 79, 76, 81, 79, 77, 76, 79, 77, 81, 79, 77, 76, 74, 72, 76, 74, 77, 76, 74, 72, 71, 69, 72, 71, 74, 72, 71, 69, 67, -66, 69, 67, 71, 69, 62, 72, 71, 72, 74, 71, 69, 67, -66, 64, 67, -66, 69, 67, 71, 69, 72, 71, 74, 72, 76, 74, 71, 72, 74, 79, 71, 72, 71, 69, 67, 67, 67, 69, 71, 72, 69, 71, 67, -66, 69, 71, 72, 74, 71, 72, 69, 71, 74, 72, 71, 69, 72, 71, 74, 72, 76, 74, 72, 71, 74, -73, 76, 74, -73, 74, 76, 77, 69, 71, -73, 74, -66, -68, 69, 71, 72, 74, 64, -66, -68, 69, -66, -68, 64, 76, 74, 72, 76, 74, 72, 71, 74, 72, 81, 80, 83, 81, 76, 77, 74, 68, 77, 76, 74, 72, 74, 72, 71, 69, 69, 81, 79, 77, 76, 79, 77, 81, 79, 76, 77, 79, 81, 77, 79, 76, 77, 79, 77, 76, 74, 77, 76, 79, 77, 74, 76, 77, 79, 76, 77, 74, 76, 72, 74, 76, 77, 74, 76, 72, 74, 76, 77, 79, 81, 77, 79, 76, 77, 79, 81, 83, 84, 81, 83, 79, 84, 79, 76, 77, 76, 74, 72, 72, 70, 69, 67, 65, 69, 67, 70, 69, 71, 72, 64, 62, 72, 65, 71, [ 64, 67, 72 ]];

		// rhythm right hand Bach Invention n.1
		bach_RH_R = [ -1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 0.5, 0.5, 1.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 0.5, 0.5, 1.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 0.5, 0.5, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.5, 0.5, 1.0, 1.0, 0.5, 0.5, 1.0, 1.0, 1.0, 2.0, -7.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, -7.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, -7.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, -7.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 4.0, -1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.5, 0.5, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 9.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 0.5, 0.5, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 16.0]/4;

		// pitches left hand Bach Invention n.1
		bach_LH_P = [ 48, 50, 52, 53, 50, 52, 48, 55, 43, 55, 57, 59, 60, 57, 59, 55, 60, 59, 60, 62, 64, 55, 57, 59, 60, 52, -54, 55, 57, 59, 60, 50, 52, -54, 55, 52, -54, 50, 55, 47, 48, 50, 52, -54, 55, 52, 47, 48, 50, 38, 43, 45, 47, 48, 45, 47, 43, 50, 55, -54, 55, 57, 50, 52, -54, 55, 52, -54, 50, 57, 62, 60, 62, 55, 67, 65, 64, 62, 65, 64, 67, 65, 64, 65, 62, 64, 69, 67, 65, 64, 67, 65, 69, 67, 65, 67, 64, 65, 70, 69, 67, 65, 69, 67, 70, 69, 67, 65, 64, 62, 65, 64, 67, 65, 64, 62, 60, 59, 62, 60, 64, 62, 60, 59, 57, -56, 59, 57, 60, 59, 52, 62, 60, 62, 60, 59, 57, 55, -54, 57, -56, 59, 57, 60, 59, 62, 60, 64, 62, 65, 64, 57, 64, 52, 57, 45, 64, 62, 60, 59, 62, 61, 64, 62, 57, 59, 60, 62, 59, 60, 57, 59, 62, 60, 59, 57, 60, 59, 62, 60, 55, 57, 58, 60, 57, 58, 55, 57, 58, 57, 55, 53, 62, 60, 58, 57, 65, 64, 62, 64, 50, 52, 53, 55, 52, 53, 50, 52, 48, 50, 52, 53, 50, 52, 53, 55, 43, [ 36, 48 ] ];

		// rhythm left hand Bach Invention n.1
		bach_LH_R = [ -9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, -5.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 5.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 3.0, 1.0, 2.0, 2.0, -1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 0.5, 0.5, 3.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, -5.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 9.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 9.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 9.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 16.0]/4;

		^this.new([bach_RH_P, bach_LH_P], [bach_RH_R, bach_LH_R],
			clefs: [\g, \f],
			windowTitle: "J.S. Bach: Inventions for Two Voices No.1 in C major, BWV 772",
			defaultSpacing: 100,
			staffNames: ["Right hand", "Left Hand"]
		);
	}

}

// Two super simple methods:
+ Array {

	// To get the number of rows of an array. This is because I want numRows(1-dimensional Array) = 1
	numRows {
		var result = this.size;
		if(this.shape[1]==nil, {result = 1;} );
		^result;
	}

	// If I have a 1-dim array, I encapsulate it, so the shape changes from [n] to [1, n]
	fixShape{
		var result = this;
		if(this.shape[1]==nil, {result = [result];} );
		^result;
	}
}