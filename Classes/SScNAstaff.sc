/**********************************************************************
SScNAstaff

A single (grand) staff: clef, optional name, vertical position.

It only knows its own geometry: where its lines are, where its clefs go,
which ledger lines a given pitch/posY needs.

It does NOT interpret music: SScNA does that.
**********************************************************************/

SScNAstaff {
	classvar layout;

	var <clef, <>name, <top, <spaceInterline;
	var <bounds; // one Event per sub-staff, sorted by pitch (low -> high)
	var <events; // SScNAevents, always sorted by absTime

	*initClass {
		// [clef char, min occurrences, midiLow, midiHigh, staff y offset, clef y offset, glyph]
		// NB: y offsets assume spaceInterline = 10
		layout = [
			[$g, 2, 86, 102, -70, -70, \gClef15ma],
			[$g, 1, 62,  80,   0,   0, \gClef],
			[$f, 1, 41,  59,  60,  40, \fClef],
			[$f, 2, 17,  35, 130, 110, \fClef15mb]
		];
	}

	*new { |clef = \g, name, top = 200, spaceInterline = 10|
		^super.newCopyArgs(clef.asString, name, top, spaceInterline).init;
	}

	init {
		events = List.new;
		this.computeBounds;
	}

	// insert keeping the order by absTime (equal onsets keep insertion order)
	addEvent { |event|
		var idx;
		idx = events.detectIndex({ |e| e.absTime > event.absTime }) ?? { events.size };
		events.insert(idx, event);
	}

	clearEvents {
		events = List.new
	}

	endTime {
		^events.collect({ |e| e.endTime }).maxItem ? 0
	}

	// setters that keep the geometry in sync
	clef_ { |newClef| clef = newClef.asString; this.computeBounds }
	top_ { |newTop| top = newTop; this.computeBounds }
	spaceInterline_ { |value| spaceInterline = value; this.computeBounds }

	computeBounds {
		var lineSpan = 4 * spaceInterline;
		bounds = layout
		.select({ |spec| clef.occurrencesOf(spec[0]) >= spec[1] })
		.collect({ |spec|
			(
				low: spec[2],
				high: spec[3],
				top: top + spec[4],
				bottom: top + spec[4] + lineSpan,
				clefY: top + spec[5],
				glyph: spec[6]
			)
		})
		.sort({ |a, b| a[\low] < b[\low] });

		if(bounds.isEmpty) {
			Error("SuperScoreStaff: clef must contain at least one 'g' or 'f', got '%'".format(clef)).throw;
		};
	}

	topY { ^bounds.collect({ |b| b[\top] }).minItem }
	bottomY { ^bounds.collect({ |b| b[\bottom] }).maxItem }

	// y of a diatonic position: diatonicIndex 0..7 (C..C), octave as in MIDI (C4 = 60)
	yForStep { |diatonicIndex, octave|
		var halfSpace = spaceInterline / 2;
		^top + (halfSpace * (38 - diatonicIndex - (7 * octave)));
	}

	// draw staff lines, clefs and (optional) name. width is in unscaled score units
	draw { |width, font, fontOffsetY, nameFont|
		bounds.do({ |b|
			Pen.strokeColor_(Color.black);
			Pen.width_(1);
			// draw the staff
			5.do({ |i|
				var y = b[\top] + (i * spaceInterline);
				Pen.line(Point(0, y), Point(width, y));
			});
			Pen.stroke;
			//draw the clefs
			Pen.stringAtPoint(
				SScNAglyphs.at(b[\glyph]),
				5 @ (b[\clefY] - 46 - fontOffsetY + 76),
				font,
				Color.black
			);
		});
		// draw the staff name (if any)
		if(name.notNil) {
			Pen.stringAtPoint(
				name.asString,
				5 @ (this.topY - (4.5*spaceInterline)),
				nameFont ?? { Font("Baskerville", 18) }, //Font.sansSerif(12)
				Color.black
			);
		};
	}

	// pure geometry: y positions of the ledger lines needed by a note
	ledgerLineYs { |pitch, posY|
		var b, num;
		b = bounds.detect({ |x| (pitch >= x[\low]) and: { pitch <= x[\high] } });
		// between staves or outside all staves: snap to nearest
		if(b.isNil) {
			b = bounds.minItem({ |x| min((pitch - x[\high]).abs, (pitch - x[\low]).abs) });
		};

		if(posY < (b[\top] - (0.5 * spaceInterline))) {
			num = ((b[\top] - posY) / spaceInterline).floor.asInteger;
			^num.collect({ |i| b[\top] - ((i + 1) * spaceInterline) });
		};
		if(posY > (b[\bottom] + (0.5 * spaceInterline))) {
			num = ((posY - b[\bottom]) / spaceInterline).floor.asInteger;
			^num.collect({ |i| b[\bottom] + ((i + 1) * spaceInterline) });
		};
		^[];
	}

	drawLedgerLines { |pitch, posX, posY, color|
		var ys = this.ledgerLineYs(pitch, posY);
		if(ys.isEmpty) { ^this };
		Pen.strokeColor_(color);
		Pen.width_(1);
		ys.do({ |y|
			Pen.line(Point(posX - spaceInterline, y), Point(posX + spaceInterline, y));
		});
		Pen.stroke;
	}
}