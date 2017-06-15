package controllers

import java.util.{Optional, UUID}

import com.example.auction.search.api.{SearchItem, SearchRequest, SearchService}
import com.example.auction.user.api.UserService
import com.example.auction.utils.PaginatedSequence
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.mvc.Action

import scala.concurrent.ExecutionContext

class SearchController(messagesApi: MessagesApi, userService: UserService, searchService: SearchService)
                       (implicit ec: ExecutionContext) extends AbstractController(messagesApi, userService) {

  def searchForm = Action.async { implicit rh =>
    withUser(loadNav(_).map { implicit nav =>
      Ok(views.html.searchItem(SearchItemForm.fill(SearchItemForm()), Optional.empty()))
    })
  }

  def search = Action.async { implicit rh =>
      SearchItemForm.form.bindFromRequest().fold(
        errorForm => {
          withUser(loadNav(_).map { implicit nav =>
            Ok(views.html.searchItem(errorForm, Optional.empty()))
          })
        },
        searchForm => {
          withUser(userId => for {
            nav <- loadNav(userId)
            searchResult <- searchService.search(searchForm.pageNumber, 15).invoke(SearchRequest(
              Some(searchForm.keywords),
              Some(searchForm.maximumPrice),
              Some(searchForm.maximumPriceCurrency.name)))
          } yield{
            Ok(views.html.searchItem(SearchItemForm.fill(SearchItemForm()), Optional.of(new PaginatedSequence[SearchItem](
              searchResult.items,
              searchResult.pageNo,
              searchResult.pageSize,
              searchResult.numResults)))(nav))
          })
        }
      )
  }
}

case class SearchItemForm(
  pageNumber: Int = 0,
  keywords: String = "",
  maximumPriceCurrency: Currency = Currency.USD,
  maximumPrice: BigDecimal = BigDecimal.decimal(0)
)

object SearchItemForm {
  import FormMappings._

  val form = Form(
    mapping(
      "pageNumber" -> number,
      "keywords" -> nonEmptyText,
      "maximumPriceCurrency" -> currency,
      "maximumPrice" -> bigDecimal
        .verifying("invalid.maxPrice.positive.or.zero", _ >= 0)
    )(SearchItemForm.apply)(SearchItemForm.unapply)
  )

  def fill(searchForm: SearchItemForm): Form[SearchItemForm] = form.fill(searchForm)
  /*
  def bind(implicit request: Request[AnyContent]): Form[SearchItemForm] = {
    val boundForm = form.bindFromRequest()
    boundForm.fold(identity, searchForm => {
      Seq(
        {
          if (!searchForm.currency.isValidStep(searchForm.increment.doubleValue())) {
            Some(FormError("increment", "invalid.step"))
          } else None
        }, {
          if (!searchForm.currency.isValidStep(searchForm.reserve.doubleValue())) {
            Some(FormError("reserve", "invalid.step"))
          } else None
        }, {
          // Make sure that the increment and reserve are multiples of 50c - in a real app, this would be more complex
          // and based on currency specific rules, for now we'll assume currencies that have cents.
          if (!(searchForm.increment * 2).isValidInt) {
            Some(FormError("increment", "invalid.increment"))
          } else {
            val incrementInt = (searchForm.increment * 2).toIntExact
            if (incrementInt <= 0) {
              Some(FormError("increment", "invalid.increment"))
            } else if (incrementInt >= 100) {
              Some(FormError("increment", "invalid.increment"))
            } else None
          }
        }, {
          if (!(searchForm.reserve * 2).isValidInt) {
            Some(FormError("reserve", "invalid.reserve"))
          } else {
            val reserveInt = (searchForm.reserve * 2).toIntExact
            if (reserveInt < 0) {
              Some(FormError("reserve", "invalid.reserve"))
            } else if (reserveInt >= 20000) {
              Some(FormError("reserve", "invalid.reserve"))
            } else None
          }
        }, {
          val duration = Duration.of(searchForm.duration, searchForm.durationUnits)
          if (duration.compareTo(Duration.ofDays(7)) > 0) {
            Some(FormError("duration", "invalid.duration"))
          } else if (duration.compareTo(Duration.ofSeconds(10)) < 0) {
            Some(FormError("duration", "invalid.duration"))
          } else None
        }
      ).foldLeft(boundForm) {
        case (form, Some(error)) => form.withError(error)
        case (form, None) => form
      }

    })
  }
  */
}