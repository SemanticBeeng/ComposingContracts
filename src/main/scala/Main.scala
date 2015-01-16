import java.time.LocalDate
import scala.math.Ordering.Implicits._


import ComposingContracts._
import ComposingContractsLatticeImplementation._

/** *
  *
  * @author Shahbaz Chaudhary (shahbazc gmail com)
  *
  *         Thanks to Simon Peyton Jones and Jean-Marc Eber
  *
  *         Thanks to #scala members on freenode:
  *         tpolecat
  *         Kristien
  *         OlegYch
  *
  *         Contracts presentation:
  *         http://www.cs.uu.nl/docs/vakken/afp/Presentations/Contracts.pdf
  *
  *         Pricing composable contracts on the GP-GPU:
  *         http://hiperfit.dk/pdf/WerkAhnfelt_2011-10ab.pdf
  *
  *         AlexanderAA's haskell implementation
  *         https://github.com/AlexanderAA/haskell-contract-valuation
  *
  *         Anton Van Straaten
  *         http://contracts.scheming.org
  */
/*
TODO:
clean up Environment, 'lookup' is too generic
actually price a stock options and compare to the real thing
 */
object Main extends App {
  implicit val LocalDateOrdering = scala.math.Ordering.fromLessThan[java.time.LocalDate]{case (a,b) => (a compareTo b) < 0}
  println(LocalDate.now()>LocalDate.now())

  //Custom combinators
  //def at(date: LocalDate): cc.Obs[Boolean] = cc.Lift2Obs((a: LocalDate, b: LocalDate) => a.compareTo(b) == 0, cc.DateObs(), cc.Konst(date))

  //custom contract
  def zcb(maturity:LocalDate, notional:Double, currency:String) = When(maturity, Scale(Const(notional),One(currency)))
  def europeanCallOption(at:LocalDate, c1:Contract, strike:Double) =
    When(
      at,
      Or(And(c1,Give(Scale(Const(strike),One("USD")))), Zero())
    )
  def americanCallOption(at:LocalDate, c1:Contract, strike:Double) =
    Anytime(
      at,
      Or(And(c1,Give(Scale(Const(strike),One("USD")))), Zero())
    )

  //custom observable
  def and(a:Obs[Boolean], b:Obs[Boolean]):Obs[Boolean] = Lift2[Boolean,Boolean,Boolean]((a,b)=>a && b, a, b)
  def between(date1:LocalDate, date2:LocalDate):Obs[Boolean] = and(Const(date1) >= DateObs(), Const(date2) <= DateObs())
  val msftProcess = ComposingContractsLatticeImplementation.binomialPriceTree(365,1.21,.0515)
  val msft = Scale(Lookup("MSFT"),One("USD"))


  //Tests
  val exchangeRates = collection.mutable.Map(
    "USD" -> ComposingContractsLatticeImplementation. binomialPriceTree(365,1,0),
    "GBP" -> ComposingContractsLatticeImplementation.binomialPriceTree(365,1.55,.0467),
    "EUR" -> ComposingContractsLatticeImplementation.binomialPriceTree(365,1.21,.0515)
  )
  val lookup = collection.mutable.Map(
    "MSFT" -> ComposingContractsLatticeImplementation. binomialPriceTree(365,1,0),
    "ORCL" -> ComposingContractsLatticeImplementation.binomialPriceTree(365,1.55,.0467),
    "EBAY" -> ComposingContractsLatticeImplementation.binomialPriceTree(365,1.21,.0515)
  )
  val marketData = Environment(
    ComposingContractsLatticeImplementation.binomialPriceTree(365,.05,.05), //interest rate (use a universal rate for now)
    exchangeRates, //exchange rates
    lookup
  )


  //portfolio test
  val portfolio = Array(
    One("USD")
    ,One("EUR")
    ,Scale(Const(-1),One("USD"))
    ,Give(One("USD"))
    ,Give(Give(One("USD")))
    ,And(One("USD"), One("USD"))
    ,And(One("USD"), Give(One("USD")))
    //,cc.When(at(LocalDate.now().plus(2, ChronoUnit.DAYS)), cc.One("USD"))
    ,When(LocalDate.now(),One("USD"))
    ,When(LocalDate.now().plusDays(5),One("USD"))
    //,cc.When(LocalDate.now().plus(5, ChronoUnit.DAYS), cc.Scale(msft, cc.One("USD")))
    ,europeanCallOption(LocalDate.now().plusDays(5),One("USD"),.5)
    //,americanOption(One("USD"),LocalDate.now().plusDays(10))
  )

  for(contract <- portfolio){
    println("===========")
    val propt = ComposingContractsLatticeImplementation.contractToPROpt(contract)
    val rp = ComposingContractsLatticeImplementation.binomialValuation(propt, marketData)
    println("Contract: "+contract)
    println("Random Process(for optimization): "+propt)
    println("Present val: "+rp(0)(0))
    println("Random Process: \n"+rp)
    //println("Random Variable contents: ")
    //rv.printLattice()
  }

}