package org.hl7.tinkar.coordinate.view.calculator;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.util.functional.QuadConsumer;
import org.hl7.tinkar.common.util.functional.TriConsumer;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.Field;
import org.hl7.tinkar.entity.PatternEntityVersion;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.terms.*;

public interface ViewCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate, NavigationCalculatorDelegate {


}
