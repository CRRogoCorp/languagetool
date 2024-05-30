package org.languagetool.rules.uk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.rules.uk.SearchHelper.Condition;
import org.languagetool.rules.uk.SearchHelper.Match;
import org.languagetool.tagging.uk.IPOSTag;
import org.languagetool.tagging.uk.PosTagHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenAgreementPrepNounExceptionHelper {
  private static final Logger logger = LoggerFactory.getLogger(TokenAgreementPrepNounExceptionHelper.class);

  private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
      "ім'я", "прізвище"
      ));

  //|лиш(е(нь)?)?
  private static final Pattern PART_INSERT_PATTERN = Pattern.compile("бодай|буцім(то)?|геть|дедалі|десь|іще|ледве|мов(би(то)?)?|навіть|наче(б(то)?)?|неначе(бто)?|немов(би(то)?)?|ніби(то)?"
      + "|попросту|просто(-напросто)?|справді|усього-на-всього|хай|хоча?|якраз|ж|би?");

  public enum Type { none, exception, skip }
  
  public static class RuleException {
    public final Type type;
    public final int skip;

    public RuleException(Type type) {
      this.type = type;
      this.skip = 0;
      if( type == Type.exception ) {
        logException();
      }
    }
    public RuleException(int skip) {
      this.type = Type.skip;
      this.skip = skip;
    }

  }

  
  public static RuleException getExceptionInfl(AnalyzedTokenReadings[] tokens, int i, AnalyzedTokenReadings prepTokenReadings, Set<String> posTagsToFind) {
    AnalyzedTokenReadings tokenReadings = tokens[i];
    String token = tokenReadings.getCleanToken();
    String prep = prepTokenReadings.getCleanToken().toLowerCase();

    
    // на дивом уцілілій техніці
    if( "дивом".equals(tokenReadings.getToken()) )
      return new RuleException(0);

    // за двісті метрів
    if( PosTagHelper.hasPosTag(tokenReadings, Pattern.compile("numr:.:v_naz.*")) ) {
      return new RuleException(Type.exception);
    }

    //TODO: only for subset: президенти/депутати/мери/гості... or by verb піти/йти/балотуватися/записатися...
    if( prep.matches("в|у|межи|між|на") ) {
      if( PosTagHelper.hasPosTag(tokenReadings, Pattern.compile("noun:anim:p:v_naz(?!:&).*")) ) { // but not &pron:
        return new RuleException(Type.exception);
      }
    }

    if ("на".equals(prep)) {
      // 1) на (свято) Купала, на (вулиці) Мазепи, на (вулиці) Тюльпанів
      if ((Character.isUpperCase(token.charAt(0)) && PosTagHelper.hasPosTag(tokenReadings, Pattern.compile("noun.*?:.:v_rod.*")))
          // 2) поміняти ім'я на Захар; поміняв Іван на Петро
          || (PosTagHelper.hasPosTag(tokenReadings, Pattern.compile(".*[fl]name.*"))
              && ((i > 1 && NAMES.contains(tokens[i-2].getAnalyzedToken(0).getToken()))
                  || (i > 2 && NAMES.contains(tokens[i-3].getAnalyzedToken(0).getLemma()))))) {
        return new RuleException(Type.exception);
      }

      // handled by xml rule
      if( "манер".equals(token) ) {
        return new RuleException(Type.exception);
      }
      // на біс (можливо краще tag=intj?)
      if( "біс".equalsIgnoreCase(token) ) {
        return new RuleException(Type.exception);
      }
    }

    // TODO: temporary until we have better logic - skip
    // при їх виборі
    if( "при".equals(prep) ) {
      if( "їх".equals(token) ) {
        return new RuleException(Type.skip);
      }
    }
    else
      if( "з".equals(prep) ) {
        if( "рана".equals(token) ) {
          return new RuleException(Type.exception);
        }
      }
      else
        if( "від".equals(prep) ) {
          if( "а".equalsIgnoreCase(token) || "рана".equals(token) || "корки".equals(token) || "мала".equals(token) ) {  // корки/мала ловиться іншим правилом
            return new RuleException(Type.exception);
          }
        }
        else if( "до".equals(prep) ) {
          if( "я".equalsIgnoreCase(token) || "корки".equals(token) || "велика".equals(token) ) {  // корки/велика ловиться іншим правилом
            return new RuleException(Type.exception);
          }
        }

    
    // exceptions
    if( tokens.length > i+1 ) {
      //      if( tokens.length > i+1 && Character.isUpperCase(tokenReadings.getAnalyzedToken(0).getToken().charAt(0))
      //        && hasRequiredPosTag(Arrays.asList("v_naz"), tokenReadings)
      //        && Character.isUpperCase(tokens[i+1].getAnalyzedToken(0).getToken().charAt(0)) )
      //          continue; // "у Конан Дойла", "у Робін Гуда"

      if( LemmaHelper.isCapitalized( token ) 
          && LemmaHelper.CITY_AVENU.contains( tokens[i+1].getAnalyzedToken(0).getToken().toLowerCase() ) ) {
        return new RuleException(Type.exception);
      }

      if( (PosTagHelper.hasPosTagStart(tokens[i+1], "num")
            || "$".equals(tokens[i+1].getToken()))
          && ("мінус".equals(token) || "плюс".equals(token)
              || "мінімум".equals(token) || "максимум".equals(token) ) ) {
        return new RuleException(Type.exception);
      }

      // на мохом стеленому дні - пропускаємо «мохом»
      if( PosTagHelper.hasPosTag(tokenReadings, "noun.*?:v_oru.*")
          && tokens[i+1].hasPartialPosTag("adjp:pasv") ) {
        return new RuleException(1);
      }

      if( "святая".equals(token)
          && "святих".equals(tokens[i+1].getToken()) ) {
        return new RuleException(Type.exception);
      }

      if( ("через".equalsIgnoreCase(prep) || "на".equalsIgnoreCase(prep))  // років 10, відсотки 3-4
          && (PosTagHelper.hasPosTagStart(tokenReadings, "noun:inanim:p:v_naz") 
              || PosTagHelper.hasPosTagStart(tokenReadings, "noun:inanim:p:v_rod")) // token.equals("років") 
          && (IPOSTag.isNum(tokens[i+1].getAnalyzedToken(0).getPOSTag())
              || (i<tokens.length-2
                  && LemmaHelper.hasLemma(tokens[i+1], Arrays.asList("зо", "з", "із"))
                  && tokens[i+2].hasPartialPosTag("num")) ) ) {
        return new RuleException(Type.exception);
      }

      if( ("вами".equals(token) || "тобою".equals(token) || "їми".equals(token))
          && tokens[i+1].getAnalyzedToken(0).getToken().startsWith("ж") ) {
        return new RuleException(0);
      }
      if( ("собі".equals(token) || "йому".equals(token) || "їм".equals(token))
          && tokens[i+1].getAnalyzedToken(0).getToken().startsWith("подібн") ) {
        return new RuleException(0);
      }
      if( ("усім".equals(token) || "всім".equals(token))
          && tokens[i+1].getAnalyzedToken(0).getToken().startsWith("відом") ) {
        return new RuleException(0);
      }

      if( "до".equalsIgnoreCase(prep) && "схід".equals(token) 
          && "сонця".equals(tokens[i+1].getAnalyzedToken(0).getToken()) ) {
        return new RuleException(Type.exception);
      }

      if( "«".equals(tokens[i+1].getAnalyzedToken(0).getToken()) 
          && tokens[i].getAnalyzedToken(0).getPOSTag().contains(":abbr") ) {
        return new RuleException(Type.exception);
      }

      if( tokens.length > i+2 ) {
        // спиралося на місячної давнини рішення
        if (/*prep.equals("на") &&*/ PosTagHelper.hasPosTag(tokenReadings, "adj:[mfn]:v_rod.*")) {
          String genders = PosTagHelper.getGenders(tokenReadings, "adj:[mfn]:v_rod.*");

          if ( PosTagHelper.hasPosTag(tokens[i+1], "noun.*?:["+genders+"]:v_rod.*")) {
            i += 1;
            return new RuleException(1);
          }
        }

        if (("нікому".equals(token) || "ніким".equals(token) || "нічим".equals(token) || "нічому".equals(token)) 
            && "не".equals(tokens[i+1].getAnalyzedToken(0).getToken())) {
          //          reqTokenReadings = null;
          return new RuleException(Type.skip);
        }
      }
    }

    return new RuleException(Type.none);
  }

  public static RuleException getExceptionStrong(AnalyzedTokenReadings[] tokens, int i, AnalyzedTokenReadings prepTokenReadings, Set<String> posTagsToFind) {
    AnalyzedTokenReadings tokenReadings = tokens[i];
    String token = tokenReadings.getCleanToken();
    String prep = prepTokenReadings.getCleanToken().toLowerCase();

    if( i < tokens.length - 1
        && "не".equals(tokenReadings.getToken())
        && PosTagHelper.hasPosTagStart(tokens[i+1], "ad") )
      return new RuleException(0);

    if( "дуже".equals(tokenReadings.getToken()) )
      return new RuleException(0);

    if( "до".equals(prep) ) {
      if( Arrays.asList("навпаки", "сьогодні", "тепер", "нині", "вчора", "учора").contains(token.toLowerCase()) ) {
        return new RuleException(Type.exception);
      }
    }

    if( "на".equals(prep) || "від".equals(prep) ) {
      if( Arrays.asList("сьогодні", "тепер", "нині", "вчора", "учора", "завтра", "зараз").contains(token.toLowerCase()) ) {
        return new RuleException(Type.exception);
      }
    }

    if( "за".equals(prep) ) {
      if( Arrays.asList("сьогодні", "вчора", "учора").contains(token.toLowerCase()) ) {
        return new RuleException(Type.exception);
      }
    }

    if( "в".equals(prep) ) {
      if( Arrays.asList("нікуди").contains(token.toLowerCase()) ) {
        return new RuleException(Type.exception);
      }
    }

    // замість вже самому засвоїти
    if( "замість".equals(prep) ) {
      if( new Match()
          .target(Condition.postag(Pattern.compile("verb.*:inf.*")))
          .limit(4)
          .skip(Condition.token("можна").negate())
          .mAfter(tokens, i+1) > 0 ) {
        return new RuleException(Type.exception);
      }
    }

    if( Arrays.asList("чимало", "кілька", "декілька", "якомога").contains(token.toLowerCase()) ) {
      return new RuleException(Type.exception);
    }

    // Усупереч не те що лихим
    if( new Match().tokenLine("не те").mBefore(tokens, i) > 0 ) {
      return new RuleException(Type.exception);
    }

    return new RuleException(Type.none);
  }

  public static RuleException getExceptionNonInfl(AnalyzedTokenReadings[] tokens, int i, AnalyzedTokenReadings prepTokenReadings, Set<String> posTagsToFind) {
    AnalyzedTokenReadings tokenReadings = tokens[i];
    String token = tokenReadings.getCleanToken();
//    String prep = prepTokenReadings.getCleanToken().toLowerCase();

//    if( PosTagHelper.hasPosTagPart(tokenReadings, "insert") )
//      return new RuleException(0);

    if( PosTagHelper.hasPosTagStart(tokenReadings, "part") ) {
      if( PART_INSERT_PATTERN.matcher(token.toLowerCase()).matches() ) {
        return new RuleException(0);
      }
    }

   // if( i < tokens.length - 1 && token.equals("їх") && PosTagHelper.hasPosTag(tokens[i+1], Pattern.compile("(adj|noun).*")) ) {
     // return new RuleException(Type.skip);
   // }

    if( token.matches("лиш(е(нь)?)?") ) {
      return new RuleException(0);
    }

    if( PosTagHelper.hasPosTag(tokenReadings, Pattern.compile("adv(?!p).*")) ) {
      // по швидко напруженим рукам
      if( i < tokens.length -1 
          && PosTagHelper.hasPosTagStart(tokens[i+1], "adj")
          && PosTagHelper.hasPosTagPartAll(tokenReadings, "adv") )
        return new RuleException(0);
     
      return new RuleException(Type.exception);
    }

    if( tokens.length > i+1 ) {
      // на лише їм відомому ...
      // на вже всім відомому ...
      if ( PosTagHelper.hasPosTag(tokens[i], Pattern.compile("noun:(un)?anim:.:v_dav:&pron.*")) ) {
          if( PosTagHelper.hasPosTagStart(tokens[i+1], "adj")
              && CaseGovernmentHelper.hasCaseGovernment(tokens[i+1], "v_dav") )
          {
              return new RuleException(1);
          }

          if( tokens.length > i+2
              && PosTagHelper.hasPosTagStart(tokens[i+1], "adv")
              && PosTagHelper.hasPosTagStart(tokens[i+2], "adj")
              && CaseGovernmentHelper.hasCaseGovernment(tokens[i+2], "v_dav") )
          return new RuleException(2);
        }
    }    
    if( tokens.length > i+2 ) {
      // на нічого не вартий папірець
      if ( "нічого".equals(token)
          && "не".equals(tokens[i+1].getToken())
          && PosTagHelper.hasPosTagStart(tokens[i+2], "adj")
          ) {
        return new RuleException(1);
      }
    }
    return new RuleException(Type.none);
  }


  private static void logException() {
    if( logger.isDebugEnabled() ) {
      StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[3];
      logger.debug("exception: " /*+ stackTraceElement.getFileName()*/ + stackTraceElement.getLineNumber());
    }
  }

}
